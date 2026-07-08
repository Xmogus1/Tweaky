package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.RenderOptimiser.PREFIX
import com.renderoptimiser.RenderOptimiser.logger
import com.renderoptimiser.config.PogObject
import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.impl.visual.text.TextReplacer
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.TextInputSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.NoammCompat
import com.renderoptimiser.utils.ThreadUtils
import com.renderoptimiser.websocket.CosmeticsSocket
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.Avatar
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.absoluteValue

/**
 * Noamm-style cosmetics (custom nametag names + custom player model sizes), but LIVE from a
 * user-run websocket server ([CosmeticsSocket] + cosmetics-server/server.py) instead of a static
 * HTTP JSON. The server broadcasts the full UUID→cosmetic map on every change, so grants show up
 * on all connected clients instantly.
 *
 * Admin: set the server's admin token in the "Admin Key" setting, then use
 * `/tweaky cosmetics name|size|remove|list` — the server validates the token.
 *
 * Render hooks (called from MixinAvatarRenderer / MixinFont — verified byte-identical 26.1.2/26.2):
 *  - custom SIZE: [extractRenderStateHook] stashes the GameProfile on the render state,
 *    [scaleHook] scales the PoseStack in AvatarRenderer.scale (negative Y = upside down).
 *  - custom NAME: [TextReplacer] rewrites the real name everywhere text renders (nametag/tab/chat).
 */
object Cosmetics: Feature("Custom player names & sizes, synced live from your cosmetics server.", toggled = true) {

    val serverUrl by TextInputSetting("Server Address", "wss://tweaky-cosmetics.onrender.com")
        .withDescription("WebSocket address of the cosmetics server (ws:// or wss://).")

    val customNames by ToggleSetting("Show Custom Names", true)
        .withDescription("Replace players' names with their custom cosmetic name wherever text renders.")

    val customSizes by ToggleSetting("Show Custom Sizes", true)
        .withDescription("Apply custom player model sizes.")

    val adminKey by TextInputSetting("Admin Key", "")
        .withDescription("The server's admin token. Needed only to GIVE cosmetics (/tweaky cosmetics).")

    private val reconnectButton by ButtonSetting("Reconnect") {
        if (enabled) CosmeticsSocket.reconnect()
        else NotificationManager.error("Cosmetics", "Enable the feature first.")
    }.withDescription("Drops the current connection and reconnects to the server address above.")

    /** Live cosmetics map, replaced wholesale on every server broadcast. Read by render hooks. */
    @Volatile
    var cosmeticPeople: Map<UUID, CosmeticData> = emptyMap()
        private set

    /**
     * Local copy of the last non-empty cosmetics payload. Free cloud hosts (Render) wipe the
     * server's disk on every restart — when the admin's client connects to a freshly-woken EMPTY
     * server, it pushes this backup back via the "restore" admin action.
     */
    private val backup = PogObject("cosmeticsBackup", "")

    /** True until the first cosmetics payload of a (re)connection arrives — set by [CosmeticsSocket]. */
    @Volatile
    private var firstPayload = false

    fun onSocketConnected() {
        firstPayload = true
    }

    override fun onEnable() {
        super.onEnable()
        CosmeticsSocket.start()
        ensureNamePurgeLoop()
    }

    /**
     * Every player Tweaky covers — having ANY Tweaky cosmetic disables ALL of that player's
     * NoammAddons cosmetics (names here, sizes in [scaleHook] via NoammCompat.suppressSize).
     */
    private fun coveredNames() = cosmeticPeople.values
        .filter { it.playerName.isNotEmpty() }
        .map { it.playerName }

    /**
     * Noamm refetches its cosmetics list asynchronously (boot + its Reload button), which re-adds
     * names we purged — so re-purge on a slow loop for the session. No-ops without NoammAddons.
     */
    @Volatile
    private var purgeLoopStarted = false

    private fun ensureNamePurgeLoop() {
        if (purgeLoopStarted) return
        purgeLoopStarted = true
        ThreadUtils.loop(10_000) {
            if (enabled) NoammCompat.suppressNames(coveredNames())
        }
    }

    override fun onDisable() {
        super.onDisable()
        CosmeticsSocket.stop()
        cosmeticPeople = emptyMap()
        TextReplacer.setAll(emptyMap())
    }

    // ------------------------------------------------------------------------------- incoming

    /** Called on the MC thread with each complete text frame from [CosmeticsSocket]. */
    fun handleSocketMessage(message: String) {
        val json = runCatching { JsonParser.parseString(message).asJsonObject }.getOrNull() ?: return
        when (json.get("type")?.asString) {
            "cosmetics" -> {
                val data = runCatching { json.getAsJsonObject("data") }.getOrNull() ?: return
                val map = HashMap<UUID, CosmeticData>()
                for ((key, value) in data.entrySet()) {
                    val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                    val o = runCatching { value.asJsonObject }.getOrNull() ?: continue
                    map[uuid] = CosmeticData(
                        playerName = o.get("playerName")?.asString ?: "",
                        name = o.get("name")?.asString ?: "",
                        sizeX = o.get("sizeX")?.asFloat ?: 1f,
                        sizeY = o.get("sizeY")?.asFloat ?: 1f,
                        sizeZ = o.get("sizeZ")?.asFloat ?: 1f,
                        offsetX = o.get("offsetX")?.asFloat ?: 0f,
                        offsetY = o.get("offsetY")?.asFloat ?: 0f,
                        offsetZ = o.get("offsetZ")?.asFloat ?: 0f,
                    )
                }
                cosmeticPeople = map
                TextReplacer.setAll(
                    map.values
                        .filter { it.hasCustomName && it.playerName.isNotEmpty() }
                        .associate { it.playerName to it.name }
                )
                NoammCompat.suppressNames(coveredNames())
                logger.info("Cosmetics: received ${map.size} cosmetic(s)")

                val isFirst = firstPayload
                firstPayload = false
                when {
                    // normal case: mirror the server state locally
                    map.isNotEmpty() -> backup.set(data.toString())
                    // empty right after connecting = the server likely lost its data — push our backup
                    isFirst && adminKey.value.trim().isNotEmpty() && backup.get().isNotEmpty() -> {
                        logger.info("Cosmetics: server is empty, pushing local backup")
                        adminRestore()
                    }
                    // emptied while connected = a deliberate wipe — drop the backup too
                    ! isFirst -> backup.set("")
                    // first + empty + nothing to restore: leave the backup alone
                }
            }

            "admin_result" -> {
                val ok = json.get("ok")?.asBoolean ?: false
                val msg = json.get("message")?.asString ?: "?"
                when {
                    msg.contains('\n') -> ChatUtils.modMessage(msg)
                    ok -> NotificationManager.push("Cosmetics", msg)
                    else -> NotificationManager.error("Cosmetics", msg)
                }
            }

            // Chat message pushed from the server panel (console `msg`/`msgall`). Supports & + &#hex colors.
            "message" -> {
                val text = json.get("text")?.asString?.takeIf { it.isNotBlank() } ?: return
                ChatUtils.chat(TextReplacer.styledComponent("$PREFIX $text"))
            }
        }
    }

    // ---------------------------------------------------------------------------------- admin

    fun adminSetName(target: String, name: String) = sendAdmin("name") {
        addProperty("target", target)
        addProperty("value", name)
    }

    fun adminSetSize(target: String, sizeX: Float, sizeY: Float = sizeX, sizeZ: Float = sizeX) = sendAdmin("size") {
        addProperty("target", target)
        addProperty("sizeX", sizeX)
        addProperty("sizeY", sizeY)
        addProperty("sizeZ", sizeZ)
    }

    fun adminSetOffset(target: String, x: Float, y: Float, z: Float) = sendAdmin("offset") {
        addProperty("target", target)
        addProperty("offsetX", x)
        addProperty("offsetY", y)
        addProperty("offsetZ", z)
    }

    fun adminRemove(target: String) = sendAdmin("remove") { addProperty("target", target) }

    fun adminList() = sendAdmin("list") { }

    fun adminOnline() = sendAdmin("online") { }

    fun adminMsg(target: String, text: String) = sendAdmin("msg") {
        addProperty("target", target)
        addProperty("value", text)
    }

    fun adminMsgAll(text: String) = sendAdmin("msgall") { addProperty("value", text) }

    /** Pushes the local cosmetics backup to the server (auto after host wipes; manual via command). */
    fun adminRestore() {
        val saved = backup.get()
        if (saved.isEmpty()) {
            NotificationManager.error("Cosmetics", "No local backup to restore.")
            return
        }
        val obj = runCatching { JsonParser.parseString(saved).asJsonObject }.getOrNull()
        if (obj == null) {
            NotificationManager.error("Cosmetics", "Local backup is corrupted.")
            return
        }
        sendAdmin("restore") { add("data", obj) }
    }

    private fun sendAdmin(action: String, fill: JsonObject.() -> Unit) {
        if (! enabled) {
            NotificationManager.error("Cosmetics", "Enable the Cosmetics feature first.")
            return
        }
        val key = adminKey.value.trim()
        if (key.isEmpty()) {
            NotificationManager.error("Cosmetics", "Set your Admin Key in the Cosmetics settings first.")
            return
        }
        val json = JsonObject().apply {
            addProperty("type", "admin")
            addProperty("token", key)
            addProperty("action", action)
            fill()
        }
        if (! CosmeticsSocket.send(json)) NotificationManager.error("Cosmetics", "Not connected to the cosmetics server.")
    }

    // ---------------------------------------------------------------------------- render hooks

    /** Gate for MixinFont — cheap, called per text draw. */
    @JvmStatic
    fun shouldReplaceText() = enabled && customNames.value

    // Pipeline counters for /tweaky cosmetics debug — pinpoint where the size path breaks.
    @Volatile private var dbgExtractCalls = 0L
    @Volatile private var dbgExtractStored = 0L
    @Volatile private var dbgScaleCalls = 0L
    @Volatile private var dbgScaleProfile = 0L
    @Volatile private var dbgScaleMatch = 0L
    @Volatile private var dbgScaleApplied = 0L
    private val dbgSeenAtScale = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun extractRenderStateHook(avatar: Avatar, state: AvatarRenderState) {
        dbgExtractCalls ++
        // no customSizes gate: the profile is also needed to suppress Noamm's size for covered
        // players even when Tweaky's own size rendering is toggled off
        if (! enabled) return
        if (avatar !is AbstractClientPlayer) return
        state.setData(GAME_PROFILE_KEY, avatar.gameProfile)
        dbgExtractStored ++
    }

    @JvmStatic
    fun scaleHook(state: AvatarRenderState, poseStack: PoseStack) {
        dbgScaleCalls ++
        val profile = state.getData(GAME_PROFILE_KEY) ?: return
        dbgScaleProfile ++
        if (dbgSeenAtScale.size < 8) dbgSeenAtScale.add("${profile.name} ${profile.id}")
        val data = cosmeticPeople[profile.id] ?: return
        dbgScaleMatch ++

        // ANY Tweaky cosmetic on this player disables Noamm's cosmetics for them entirely.
        NoammCompat.suppressSize(state)

        if (! customSizes.value || (! data.hasCustomSize && ! data.hasOffset)) return

        // Pose space here is already flipped by vanilla (x mirrored, y inverted, before scale()
        // is called) — negate x/y so offsets read intuitively: +y = up, x/z follow the body.
        if (data.hasOffset) poseStack.translate(- data.offsetX, - data.offsetY, data.offsetZ)

        if (data.hasCustomSize) {
            if (data.sizeY < 0) poseStack.translate(0f, data.sizeY * 2f, 0f)
            poseStack.scale(data.sizeX, data.sizeY, data.sizeZ)
        }

        state.nameTagAttachment?.let { pos ->
            var y = pos.y
            if (data.hasCustomSize) y = (y + 0.15) * data.sizeY.absoluteValue
            y += data.offsetY
            state.nameTagAttachment = Vec3(pos.x, y, pos.z)
        }
        dbgScaleApplied ++
    }

    /** Chat dump of the whole cosmetics pipeline state — run `/tweaky cosmetics debug` in-game. */
    fun printDebug() {
        val sb = StringBuilder("&9&lCosmetics debug&r\n")
        sb.append("&7feature=&f$enabled&7 names=&f${customNames.value}&7 sizes=&f${customSizes.value}&7 socket=&f${CosmeticsSocket.connected}\n")
        sb.append("&7map (&f${cosmeticPeople.size}&7):\n")
        cosmeticPeople.forEach { (id, d) ->
            val name = if (d.hasCustomName) " name='${d.name.take(40)}&7'" else ""
            val offset = if (d.hasOffset) " &doff ${d.offsetX}/${d.offsetY}/${d.offsetZ}&7" else ""
            sb.append("&8- &f${d.playerName.ifEmpty { "?" }} &7$id &b${d.sizeX}/${d.sizeY}/${d.sizeZ}&7$offset$name\n")
        }
        sb.append("&7extract: calls=&f$dbgExtractCalls&7 stored=&f$dbgExtractStored\n")
        sb.append("&7scale: calls=&f$dbgScaleCalls&7 profile=&f$dbgScaleProfile&7 match=&f$dbgScaleMatch&7 applied=&f$dbgScaleApplied\n")
        sb.append("&7seen at scale: &f${dbgSeenAtScale.joinToString().ifEmpty { "none" }}\n")
        sb.append("&7noamm: &f${NoammCompat.status()}")
        ChatUtils.chat(sb.toString())
    }

    @JvmField
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "$MOD_ID:game_profile" }

    data class CosmeticData(
        val playerName: String,
        val name: String,
        val sizeX: Float,
        val sizeY: Float,
        val sizeZ: Float,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val offsetZ: Float = 0f,
    ) {
        val hasCustomName get() = name.isNotEmpty()
        val hasCustomSize get() = sizeX != 1f || sizeY != 1f || sizeZ != 1f
        val hasOffset get() = offsetX != 0f || offsetY != 0f || offsetZ != 0f
    }
}
