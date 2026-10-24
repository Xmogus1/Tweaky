package com.renderoptimiser.features.impl.general

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.event.impl.ChatMessageEvent
import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.impl.visual.Cosmetics
import com.renderoptimiser.party.PartyTracker
import com.renderoptimiser.ui.clickgui.components.impl.KeybindSetting
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.websocket.CosmeticsSocket
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

/**
 * Party chat over the Tweaky relay (the same websocket the Cosmetics feature uses). A message goes
 * only to the party members you currently have (tracked by [PartyTracker]) who are connected to the
 * relay - whether they run Tweaky or SkyblockConnect, since both speak the same party_chat packet.
 *
 * Send with /tweaky chat <message>, or bind "Open Chat Key" to pop the chat box prefilled. Incoming
 * lines render client-side as "Party <name>: <message>"; nothing is sent to Hypixel's own party chat.
 */
object PartyChat: Feature("Talk to your party over the relay. Reaches party members who also run Tweaky or SBC.") {

    private val chatKey by KeybindSetting("Open Chat Key", InputConstants.UNKNOWN.value)
        .withDescription("Opens the chat box prefilled with /tweaky chat so you can message your party over the relay.")

    override fun init() {
        register<ChatMessageEvent> { PartyTracker.onChat(event.unformattedText) }
        register<TickEvent.Start> {
            if (chatKey.value != InputConstants.UNKNOWN.value && chatKey.isPressed() && mc.mouseHandler.isMouseGrabbed()) {
                RenderOptimiser.screen = ChatScreen("/tweaky chat ", false)
            }
        }
    }

    override fun onEnable() {
        super.onEnable()
        CosmeticsSocket.start()
    }

    override fun onDisable() {
        super.onDisable()
        if (! Cosmetics.enabled) CosmeticsSocket.stop()
    }

    fun send(messageRaw: String) {
        if (! enabled) { ChatUtils.modMessage("§cParty Chat is off. Enable it in §f/tweaky§c first."); return }
        val message = sanitize(messageRaw)
        if (message.isEmpty()) return
        if (! CosmeticsSocket.connected) { ChatUtils.modMessage("§cNot connected to the relay."); return }
        val recipients = PartyTracker.recipients()
        if (recipients.isEmpty()) {
            ChatUtils.modMessage("§cNo party members detected yet. Talk in party chat or run §f/party list §cfirst.")
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

    fun onRemote(from: String, textRaw: String) {
        if (! enabled) return
        if (PartyTracker.hasMembers() && ! PartyTracker.isMember(from)) return
        val text = sanitize(textRaw)
        if (text.isEmpty()) return
        show(from, text)
    }

    private fun show(from: String, message: String) {
        ChatUtils.chat(Component.literal("§5§lParty §r§f$from§7: §r").append(Component.literal(message)))
    }

    private fun sanitize(text: String): String = text.replace("§", "").trim().take(256)
}
