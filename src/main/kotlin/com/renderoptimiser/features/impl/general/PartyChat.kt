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

/**
 * Party chat over the Tweaky relay (the same websocket the Cosmetics feature uses). A message goes
 * only to the party members you currently have (from the Hypixel Mod API, or [PartyTracker] as a
 * fallback) who are connected to the relay - whether they run Tweaky or SkyblockConnect, since both
 * speak the same party_chat packet.
 *
 * Send with /tweaky chat <message> or /wsc <message>; bare /tweaky chat or /wsc toggles the feature.
 * Incoming lines render client-side as "Party <name>: <message>"; nothing is sent to Hypixel's own party chat.
 */
object PartyChat: Feature("Talk to your party over the relay. Reaches party members who also run Tweaky or SBC.", toggled = true) {

    private var pollCounter = 0

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
        ChatUtils.modMessage(if (enabled) "§aParty Chat §2on" else "§cParty Chat §4off")
    }

    fun send(messageRaw: String) {
        if (! enabled) { ChatUtils.modMessage("§cParty Chat is off. Type §f/wsc §cto turn it on."); return }
        val message = sanitize(messageRaw)
        if (message.isEmpty()) return
        if (! CosmeticsSocket.connected) { ChatUtils.modMessage("§cNot connected to the relay."); return }
        HypixelApi.queryParty()
        val recipients = recipients()
        if (recipients.isEmpty()) {
            val diag = if (HypixelApi.available) "" else " §8(Hypixel Mod API not installed - using chat tracking)"
            ChatUtils.modMessage("§cNo party members detected yet. Run §f/party list §conce, or talk in party chat.$diag")
            return
        }
        val ok = CosmeticsSocket.send(JsonObject().apply {
            addProperty("type", "party_chat")
            addProperty("text", message)
            add("to", JsonArray().apply { recipients.forEach(::add) })
        })
        if (! ok) { ChatUtils.modMessage("§cCouldn't send, not connected to the relay."); return }
        show(mc.user.name, message)
    }

    fun onRemote(from: String, fromUuid: String?, textRaw: String) {
        if (! enabled) return
        if (! isFromMyParty(from, fromUuid)) return
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

    private fun isFromMyParty(from: String, fromUuid: String?): Boolean {
        val uuidKnown = HypixelApi.available && HypixelApi.partyMembers.isNotEmpty()
        val nameKnown = PartyTracker.hasMembers()
        if (! uuidKnown && ! nameKnown) return true
        if (uuidKnown && fromUuid != null && HypixelApi.partyMembers.any { it.toString().equals(fromUuid, true) }) return true
        if (nameKnown && PartyTracker.isMember(from)) return true
        return false
    }

    private fun show(from: String, message: String) {
        ChatUtils.chat(Component.literal("§5§lParty §r§f$from§7: §r").append(Component.literal(message)))
    }

    private fun sanitize(text: String): String = text.replace("§", "").trim().take(256)
}
