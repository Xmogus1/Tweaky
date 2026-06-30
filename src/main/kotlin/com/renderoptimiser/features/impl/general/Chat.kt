package com.renderoptimiser.features.impl.general

import com.renderoptimiser.event.impl.MouseClickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.interfaces.IChatComponent
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils.removeFormatting
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import org.lwjgl.glfw.GLFW

object Chat: Feature("Chat tweaks: copy messages & timestamps.") {
    private val ctrlClickToCopy by ToggleSetting("Ctrl Click to Copy", true).withDescription("Ctrl + Left Click a message to copy it to your clipboard.")

    /** Read by MixinChatComponent: prepend a gray [HH:mm] to every incoming chat message. */
    @JvmStatic
    val timestamps by ToggleSetting("Timestamps", true).withDescription("Show a gray [HH:mm] time in front of chat messages.")

    /** True when the timestamp prefix should be applied (feature + setting on). */
    @JvmStatic
    fun timestampsActive(): Boolean = enabled && timestamps.value

    override fun init() {
        register<MouseClickEvent> {
            if (! ctrlClickToCopy.value) return@register
            if (mc.gui.screen() !is ChatScreen) return@register
            if (event.button != 0) return@register
            if (event.action != GLFW.GLFW_PRESS) return@register
            if (GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_CONTROL) != GLFW.GLFW_PRESS) return@register
            val message = getHoveredMsg().takeUnless { it.isBlank() } ?: return@register

            NotificationManager.push("Message copied to clipboard", message)
            mc.keyboardHandler.clipboard = message
            event.isCanceled = true
        }
    }

    private fun getHoveredMsg(): String {
        val chatHud = (mc.gui.hud.chat as? IChatComponent) ?: return ""

        val x = chatHud.mouseXtoChatX
        val y = chatHud.mouseYtoChatY
        val i = chatHud.getLineIndex(x, y)

        if (i < 0 || i >= chatHud.visibleMessages.size) return ""

        val builder = StringBuilder()
        val lines = ArrayList<GuiMessage.Line>()

        for (j in i.toInt() + 1 until chatHud.visibleMessages.size) {
            val line = chatHud.visibleMessages[j]
            if (line.endOfEntry()) break
            lines.add(0, line)
        }

        for (j in i.toInt() downTo 0) {
            val line = chatHud.visibleMessages[j]
            lines.add(line)
            if (line.endOfEntry()) break
        }

        for (line in lines) {
            line.content().accept { _, _, codePoint ->
                builder.appendCodePoint(codePoint)
                true
            }
        }

        return builder.toString().removeFormatting()
    }
}
