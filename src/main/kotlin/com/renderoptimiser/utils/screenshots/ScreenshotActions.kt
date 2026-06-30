package com.renderoptimiser.utils.screenshots

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.RenderOptimiser.logger
import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.RenderOptimiser.scope
import com.renderoptimiser.features.impl.gui.ScreenshotsMenu
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.ThreadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import org.lwjgl.util.tinyfd.TinyFileDialogs
import kotlin.random.Random

/**
 * Copy-to-clipboard, native Save-As, and async Share (keyless upload) for a single screenshot [File].
 *
 * Threading contract (see ThreadUtils):
 *  - COPY  : off-thread on [scope] (ImageIO read is I/O; clipboard set returns immediately).
 *  - SAVEAS: dedicated NON-daemon [Thread] (the modal dialog blocks its caller; must never be the MC thread).
 *  - SHARE : off-thread on [scope]; the resulting clipboard write + chat happen on the MC thread.
 *
 * No GL/texture work happens here — that lives in ScreenshotsScreen on the render thread.
 */
object ScreenshotActions {

    //#if CURSEFORGE
    //#else
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    //#endif

    // ----------------------------------------------------------------------------------------- COPY

    /** Reads the PNG and puts it on the system clipboard as an image (Windows: native; else AWT). */
    fun copyToClipboard(png: File) {
        scope.launch {
            runCatching {
                //#if CURSEFORGE
                //$ // CurseForge build: no PowerShell/AWT — copy the file path as text via GLFW instead.
                //$copyToClipboardText(png.absolutePath)
                //$NotificationManager.push("Copied file path", png.name)
                //$logger.info("Copied screenshot path to clipboard: ${png.name}")
                //#else
                if (System.getProperty("os.name").lowercase().contains("win")) copyImageWindows(png)
                else copyImageAwt(png)
                NotificationManager.push("Copied to clipboard", png.name)
                logger.info("Copied screenshot to clipboard: ${png.name}")
                //#endif
            }.onFailure {
                logger.error("Failed to copy screenshot to clipboard", it)
                NotificationManager.error("Copy failed", "${it.javaClass.simpleName}: ${it.message ?: "?"}")
            }
        }
    }

    //#if CURSEFORGE
    //#else
    /**
     * Windows clipboard via PowerShell + WinForms. Minecraft runs AWT permanently headless (the property
     * is latched before any mod code runs and un-latching needs module reflection the JDK blocks), so
     * java.awt clipboard throws HeadlessException. A short-lived powershell.exe using
     * System.Windows.Forms.Clipboard.SetImage in STA mode works, and the image persists after it exits.
     */
    private fun copyImageWindows(png: File) {
        val path = png.absolutePath.replace("'", "''")
        val ps = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; " +
            "\$img=[System.Drawing.Image]::FromFile('$path'); " +
            "[System.Windows.Forms.Clipboard]::SetImage(\$img); \$img.Dispose()"
        val proc = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-Command", ps)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        if (! proc.waitFor(20, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw RuntimeException("clipboard copy timed out")
        }
        if (proc.exitValue() != 0) throw RuntimeException("powershell exit ${proc.exitValue()}: ${out.trim().take(200)}")
    }

    /** Non-Windows fallback: AWT clipboard (only works if the JVM is not headless). */
    private fun copyImageAwt(png: File) {
        System.setProperty("java.awt.headless", "false")
        val image = ImageIO.read(png) ?: error("ImageIO.read returned null for ${png.absolutePath}")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
    }
    //#endif

    // --------------------------------------------------------------------------------------- SAVEAS

    /**
     * Opens a native Save-As dialog and copies [png] to the chosen path. The modal dialog runs on its own
     * non-daemon thread so the MC render/main thread is never blocked. Drops MC out of exclusive fullscreen
     * (on the MC thread) before opening so the OS can composite the dialog above the game, then restores it.
     */
    fun saveAs(png: File) {
        Thread({
            try {
                // Native LWJGL save dialog — no AWT, so it works even though MC runs AWT headless.
                val chosen = TinyFileDialogs.tinyfd_saveFileDialog("Save Screenshot As", png.absolutePath, null, "PNG image")
                if (chosen != null) {
                    val target = if (chosen.endsWith(".png", true)) File(chosen) else File("$chosen.png")
                    Files.copy(png.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.info("Saved screenshot to ${target.absolutePath}")
                    NotificationManager.push("Screenshot saved", target.absolutePath)
                }
                else {
                    logger.info("Save-As cancelled by user")
                }
            }
            catch (t: Throwable) {
                logger.error("Save-As dialog failed", t)
                NotificationManager.error("Save failed", t.message ?: t.javaClass.simpleName)
            }
        }, "Tweaky-SaveAs").apply { isDaemon = false; start() }
    }

    // ---------------------------------------------------------------------------------------- SHARE
    // The CurseForge build strips all external-upload functionality (reviewer requirement). Everything
    // from here to copyToClipboardText is excluded from that variant via the CURSEFORGE guard.
    //#if CURSEFORGE
    //#else

    /** Uploads [png] to the configured keyless host (or imgur, if a Client-ID is set), then copies + chats the link. */
    fun share(png: File) {
        NotificationManager.push("Uploading…", png.name)
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { Files.readAllBytes(png.toPath()) }

                val clientId = ScreenshotsMenu.imgurClientId.value.trim()
                val url = if (clientId.isNotEmpty()) uploadToImgur(bytes, clientId)
                else when (ScreenshotsMenu.uploadHost.value) {
                    1 -> uploadKeyless(bytes, png.name, "https://0x0.st", "file")
                    else -> uploadCatbox(bytes, png.name)
                }

                if (! url.startsWith("https://")) {
                    NotificationManager.error("Upload failed", url)
                    return@launch
                }

                copyToClipboardText(url)

                if (ScreenshotsMenu.chatLink.value) {
                    ChatUtils.clickableChat(
                        message = "&aUploaded! &7(click to copy link)",
                        prefix = true,
                        hover = "&e$url\n&7Link copied to clipboard",
                        copy = url,
                    )
                }
                else {
                    NotificationManager.push("Uploaded", "Link copied to clipboard")
                }
                logger.info("Uploaded screenshot ${png.name} -> $url")
            }
            catch (e: Exception) {
                logger.error("Screenshot upload failed", e)
                NotificationManager.error("Upload failed", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // --------------------------------------------------------------------------------------- upload

    private fun uploadCatbox(data: ByteArray, fileName: String): String {
        val boundary = "----TweakyBoundary${Random.nextLong().toULong().toString(16)}"
        val body = multipartBody(
            boundary = boundary,
            fields = mapOf("reqtype" to "fileupload"),
            fileFieldName = "fileToUpload",
            fileName = fileName,
            fileBytes = data,
        )
        return post("https://catbox.moe/user/api.php", "multipart/form-data; boundary=$boundary", body).trim()
    }

    /** 0x0.st and other bare-URL keyless hosts share this path — just the URL + file field name differ. */
    private fun uploadKeyless(data: ByteArray, fileName: String, endpoint: String, field: String): String {
        val boundary = "----TweakyBoundary${Random.nextLong().toULong().toString(16)}"
        val body = multipartBody(boundary, emptyMap(), field, fileName, data)
        return post(endpoint, "multipart/form-data; boundary=$boundary", body).trim()
    }

    private fun uploadToImgur(data: ByteArray, clientId: String): String {
        val boundary = "----TweakyBoundary${Random.nextLong().toULong().toString(16)}"
        val body = multipartBody(boundary, emptyMap(), "image", "image.png", data)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.imgur.com/3/image"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .header("Authorization", "Client-ID $clientId")
            .header("User-Agent", "Tweaky/${RenderOptimiser.MOD_VERSION}")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw RuntimeException("HTTP ${response.statusCode()}: ${response.body().take(200)}")
        // Minimal JSON pluck: "link":"https:\/\/i.imgur.com\/xxxx.png"
        val match = Regex(""""link"\s*:\s*"([^"]+)"""").find(response.body())
            ?: throw RuntimeException("imgur: no link in response")
        return match.groupValues[1].replace("\\/", "/")
    }

    private fun post(url: String, contentType: String, body: ByteArray): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", contentType)
            .header("User-Agent", "Tweaky/${RenderOptimiser.MOD_VERSION}")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw RuntimeException("HTTP ${response.statusCode()}: ${response.body().take(200)}")
        return response.body()
    }

    /** Builds a raw multipart/form-data body. CRLF separators are mandatory per the spec. */
    private fun multipartBody(
        boundary: String,
        fields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val crlf = "\r\n"
        fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

        for ((name, value) in fields) {
            w("--$boundary$crlf")
            w("Content-Disposition: form-data; name=\"$name\"$crlf$crlf")
            w("$value$crlf")
        }

        w("--$boundary$crlf")
        w("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$fileName\"$crlf")
        w("Content-Type: image/png$crlf$crlf")
        out.write(fileBytes)
        w(crlf)

        w("--$boundary--$crlf")
        return out.toByteArray()
    }
    //#endif

    /** Copies plain text to the system clipboard on the MC thread (used by the CurseForge Copy path + Share). */
    private fun copyToClipboardText(text: String) {
        ThreadUtils.runOnMcThread { mc.keyboardHandler.clipboard = text }
    }
}
