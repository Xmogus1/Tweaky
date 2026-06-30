package com.renderoptimiser.features.impl.general

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.RenderOptimiser.logger
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Auto-updater backed by GitHub Releases on [REPO].
 *
 * Release convention: tag = the mod version exactly (e.g. `1.1.2`, a leading `v` is tolerated),
 * with the normal jars attached using their standard names (`Tweaky-<version>-<mc>.jar`).
 *
 * Flow: on launch (and via "Check Now") fetch releases/latest → if newer, download the asset
 * matching this jar's MC version to `<jar dir>/<name>.jar.tweaky-update` (Fabric ignores non-.jar
 * files), then swap on JVM shutdown. POSIX allows deleting the running jar directly; Windows keeps
 * it locked, so the shutdown hook hands the swap to a detached `cmd` helper that runs after the
 * JVM exits. A leftover staged file (e.g. the game crashed before the swap) is re-armed on the
 * next launch. The CurseForge build strips the whole install path (their rules forbid
 * self-updating mods) and only announces new versions.
 */
object AutoUpdate: Feature("Keeps Tweaky updated automatically.", toggled = true) {
    private const val REPO = "Xmogus1/tweaky"
    //#if CURSEFORGE
    //#else
    private const val STAGED_SUFFIX = ".tweaky-update"
    //#endif

    //#if CURSEFORGE
    //#else
    private val notifyOnly by ToggleSetting("Notify Only")
        .withDescription("Only announce new versions in chat instead of auto-installing them.")
    //#endif

    private val checkNow by ButtonSetting("Check Now") { check(manual = true) }
        .withDescription("Check for a new version right now.")

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile private var busy = false
    @Volatile private var swapArmed = false
    private var announced = ""

    override fun init() {
        //#if CURSEFORGE
        //#else
        runCatching { resumeStagedUpdate() }.onFailure { logger.warn("AutoUpdate: staged-update scan failed", it) }
        //#endif
        check(manual = false)
    }

    private fun check(manual: Boolean) {
        if (! enabled && ! manual) return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val release = fetchLatestRelease()
                if (release == null) {
                    if (manual) NotificationManager.push("Auto Update", "No releases published yet.")
                    return@launch
                }
                val version = release.get("tag_name")?.asString?.removePrefix("v")?.trim()
                if (version.isNullOrEmpty()) return@launch

                if (! isNewer(version, RenderOptimiser.MOD_VERSION)) {
                    if (manual) NotificationManager.push("Auto Update", "Tweaky ${RenderOptimiser.MOD_VERSION} is up to date.")
                    return@launch
                }
                onUpdateAvailable(version, release, manual)
            }
            catch (e: Exception) {
                logger.warn("AutoUpdate: check failed", e)
                if (manual) NotificationManager.error("Auto Update", "Check failed: ${e.message ?: e.javaClass.simpleName}")
            }
            finally {
                busy = false
            }
        }
    }

    private fun onUpdateAvailable(version: String, release: JsonObject, manual: Boolean) {
        val page = release.get("html_url")?.asString ?: "https://github.com/$REPO/releases"

        if (swapArmed) {
            if (manual) NotificationManager.push("Auto Update", "Tweaky $version is already downloaded — restart to install.")
            return
        }

        //#if CURSEFORGE
        //$announceLink(version, page, manual)
        //#else
        if (notifyOnly.value) {
            announceLink(version, page, manual)
            return
        }
        runCatching { downloadAndStage(version, release) }.onFailure {
            logger.warn("AutoUpdate: auto-install failed, falling back to a link", it)
            announceLink(version, page, manual)
        }
        //#endif
    }

    /** Notify-only announcement: toast + clickable chat message that copies the release link. */
    private fun announceLink(version: String, page: String, force: Boolean) {
        if (! force && announced == version) return
        announced = version
        NotificationManager.push("Auto Update", "Tweaky $version is available!")
        ChatUtils.clickableChat(
            message = "&aTweaky &f$version&a is available! &7(click to copy the download link)",
            prefix = true,
            hover = "&e$page",
            copy = page,
        )
    }

    // ------------------------------------------------------------------- version helpers

    /** Numeric x.y.z comparison; missing parts count as 0 (so 1.2 == 1.2.0). */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun fetchLatestRelease(): JsonObject? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/$REPO/releases/latest"))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Tweaky/${RenderOptimiser.MOD_VERSION}")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null // repo has no releases yet
        if (response.statusCode() != 200) throw RuntimeException("GitHub API HTTP ${response.statusCode()}")
        return runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrNull()
    }

    private fun mcVersion(): String =
        FabricLoader.getInstance().getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("?")

    /** The running Tweaky jar, or null when not launched from a plain jar (dev environment). */
    private fun currentJarPath(): Path? {
        val container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null) ?: return null
        val path = runCatching { container.origin.paths.firstOrNull() }.getOrNull() ?: return null
        return path.takeIf { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
    }

    //#if CURSEFORGE
    //#else
    // ------------------------------------------------------------------- download + install

    private fun downloadAndStage(version: String, release: JsonObject) {
        val currentJar = currentJarPath() ?: error("not running from a jar")
        val mc = mcVersion()
        val wanted = "Tweaky-$version-$mc.jar"

        val asset = release.getAsJsonArray("assets")
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name")?.asString == wanted }
            ?: error("release $version has no asset named $wanted")

        val url = asset.get("browser_download_url")?.asString ?: error("asset $wanted has no download url")
        val expectedSize = asset.get("size")?.asLong ?: - 1L

        val dir = currentJar.parent
        val staged = dir.resolve(wanted + STAGED_SUFFIX)

        // already downloaded (e.g. a previous session that never exited cleanly)? just re-arm
        if (! Files.exists(staged) || (expectedSize > 0 && Files.size(staged) != expectedSize)) {
            val tmp = Files.createTempFile(dir, "tweaky-dl", ".tmp")
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "Tweaky/${RenderOptimiser.MOD_VERSION}")
                    .GET()
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofFile(tmp))
                if (response.statusCode() != 200) error("download HTTP ${response.statusCode()}")
                if (expectedSize > 0 && Files.size(tmp) != expectedSize) error("download incomplete (${Files.size(tmp)}/$expectedSize bytes)")
                Files.move(tmp, staged, StandardCopyOption.REPLACE_EXISTING)
            }
            catch (e: Exception) {
                runCatching { Files.deleteIfExists(tmp) }
                throw e
            }
        }

        armSwap(staged, dir.resolve(wanted), currentJar, version)
    }

    /** Registers the on-exit jar swap (once) and tells the player an update is pending. */
    private fun armSwap(staged: Path, target: Path, oldJar: Path, version: String) {
        if (swapArmed) return
        swapArmed = true

        NotificationManager.push("Auto Update", "Tweaky $version downloaded — installs when you close the game.", 6000L)
        ChatUtils.modMessage("&aTweaky &f$version&a downloaded! It installs itself when you close the game.")
        logger.info("AutoUpdate: staged $staged -> $target (replacing ${oldJar.fileName})")

        Runtime.getRuntime().addShutdownHook(Thread({
            try {
                // POSIX lets us unlink the running jar directly.
                Files.delete(oldJar)
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING)
            }
            catch (_: Exception) {
                // Windows keeps the running jar locked until the JVM dies — hand the swap to a
                // detached shell that outlives us. The "if not exist" guard makes sure we never
                // end up with BOTH jars present (Fabric would crash on the duplicate mod id).
                runCatching {
                    ProcessBuilder(
                        "cmd", "/c",
                        "ping -n 3 127.0.0.1 > nul & del /f /q \"$oldJar\" & if not exist \"$oldJar\" move /y \"$staged\" \"$target\""
                    ).start()
                }
            }
        }, "Tweaky-Updater"))
    }

    /** Handles a staged jar left over from a previous session (crash before the swap ran). */
    private fun resumeStagedUpdate() {
        val currentJar = currentJarPath() ?: return
        val mc = mcVersion()
        val dir = currentJar.parent

        Files.list(dir).use { stream ->
            stream.filter {
                val n = it.fileName.toString()
                n.startsWith("Tweaky-") && n.endsWith("-$mc.jar$STAGED_SUFFIX")
            }.forEach { staged ->
                val jarName = staged.fileName.toString().removeSuffix(STAGED_SUFFIX)
                val version = jarName.removePrefix("Tweaky-").substringBefore('-')
                if (isNewer(version, RenderOptimiser.MOD_VERSION)) armSwap(staged, dir.resolve(jarName), currentJar, version)
                else runCatching { Files.delete(staged) } // already applied or stale
            }
        }
    }
    //#endif
}
