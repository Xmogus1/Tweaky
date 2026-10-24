package com.renderoptimiser.features.impl.general

import com.renderoptimiser.event.impl.ChatMessageEvent
import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.impl.visual.Cosmetics
import com.renderoptimiser.hypixel.HypixelApi
import com.renderoptimiser.party.PartyTracker
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.websocket.CosmeticsSocket
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

/**
 * Party chat over the Tweaky relay (the same websocket the Cosmetics feature uses). A message goes
 * only to the party members you currently have (from the Hypixel Mod API, or [PartyTracker] as a
 * fallback) who are connected to the relay - whether they run Tweaky or SkyblockConnect, since both
 * speak the same party_chat packet.
 *
 * Send with /tweaky chat <message> or /wsc <message>; bare /tweaky chat or /wsc toggles the feature.
 * Incoming lines render client-side as "Party <name>: <message>"; nothing is sent to Hypixel's own party chat.
 */
object PartyChat: Feature("Party chat over the relay. While ON, your typed chat goes to your party instead of public chat.") {

    private var pollCounter = 0

    /** Set by ChatUtils around its own sendChat so mod-initiated messages are never redirected to the party. */
    @Volatile
    var passthrough = false

    override fun init() {
        HypixelApi.init()
        register<ChatMessageEvent> { PartyTracker.onChat(event.unformattedText) }
        register<TickEvent.Start> {
            if (++ pollCounter >= 100) { pollCounter = 0; HypixelApi.queryParty() }
        }
    }

    override fun onEnable() {
        super.onEnable()
        CosmeticsSocket.start()
        HypixelApi.queryParty()
    }

    override fun onDisable() {
        super.onDisable()
        if (! Cosmetics.enabled) CosmeticsSocket.stop()
    }

    fun toggleFeature() {
        toggle()
        ChatUtils.modMessage(
            if (enabled) "§aParty Chat §2on §7- your chat now goes to your party"
            else "§cParty Chat §4off §7- back to normal chat"
        )
    }

    /** Called from MixinClientPacketListener for every chat message you type (commands are separate).
     *  Returns true to swallow the vanilla public-chat send, so a party message never leaks to skyblock chat. */
    @JvmStatic
    fun interceptOutgoing(message: String): Boolean {
        if (passthrough || ! enabled || message.isBlank()) return false
        send(message)
        return true
    }

    fun send(messageRaw: String): Boolean {
        if (! enabled) { ChatUtils.modMessage("§cParty Chat is off. Type §f/wsc §cto turn it on."); return false }
        val message = sanitize(messageRaw)
        if (message.isEmpty()) return false
        if (! CosmeticsSocket.connected) { ChatUtils.modMessage("§cNot connected to the relay."); return false }
        HypixelApi.queryParty()
        val recipients = recipients()
        if (recipients.isEmpty()) {
            val diag = if (HypixelApi.available) "" else " §8(Hypixel Mod API not installed - using chat tracking)"
            ChatUtils.modMessage("§cNo party members detected yet. Run §f/party list §conce, or talk in party chat.$diag")
            return false
        }
        val ok = CosmeticsSocket.send(JsonObject().apply {
            addProperty("type", "party_chat")
            addProperty("text", message)
            add("to", JsonArray().apply { recipients.forEach(::add) })
        })
        if (! ok) { ChatUtils.modMessage("§cCouldn't send, not connected to the relay."); return false }
        show(mc.user.name, message)
        return true
    }

    fun onRemote(from: String, fromUuid: String?, textRaw: String) {
        if (! enabled) return
        val text = sanitize(textRaw)
        if (text.isEmpty()) return
        show(from, text)
    }

    private fun recipients(): List<String> {
        val self = runCatching { mc.user.profileId }.getOrNull()
        val uuids = if (HypixelApi.available) HypixelApi.partyMembers.filter { it != self }.map { it.toString() } else emptyList()
        if (uuids.isNotEmpty()) return uuids
        return PartyTracker.recipients()
    }

    /** Inject the message back through the real chat pipeline as a "Party > Name: ..." line, so
     *  party-chat mods (Odin's !commands, etc.) parse it. Falls back to a plain client line. */
    private fun show(from: String, message: String) {
        val line = Component.literal("§9Party §8> §b$from§f: §r").append(Component.literal(message))
        val conn = mc.player?.connection
        if (conn == null) { ChatUtils.chat(line); return }
        runCatching { conn.handleSystemChat(ClientboundSystemChatPacket(line, false)) }
            .onFailure { ChatUtils.chat(line) }
    }

    private fun sanitize(text: String): String = text.replace("§", "").trim().take(256)
}
