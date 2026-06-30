package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.features.Feature
import com.renderoptimiser.mixin.IKeyMapping
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.KeybindSetting
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

object GuiMove: Feature("Move in containers; hold a key for chat.") {

    private val chatKey by KeybindSetting("Chat move key", GLFW.GLFW_KEY_LEFT_CONTROL)
        .withDescription("Hold this to walk while the chat box is open.")

    private val movementKeys: List<KeyMapping>
        get() = mc.options.let {
            listOf(it.keyUp, it.keyDown, it.keyLeft, it.keyRight, it.keyJump, it.keyShift, it.keySprint)
        }

    /**
     * Called from [com.renderoptimiser.mixin.MixinKeyMapping] on KeyMapping#isDown.
     *
     * @return null to keep the original value, or the overridden pressed state when GuiMove
     *         should drive this movement bind from the physical key.
     */
    @JvmStatic
    fun overrideIsDown(mapping: KeyMapping): Boolean? {
        if (! enabled) return null

        val screen = mc.gui.screen() ?: return null

        val allowMovement = when (screen) {
            is AbstractContainerScreen<*> -> true
            is ChatScreen -> chatKey.isDown()
            else -> false
        }
        if (! allowMovement) return null

        if (mapping !in movementKeys) return null

        val key = (mapping as IKeyMapping).key
        if (key.value == GLFW.GLFW_KEY_UNKNOWN) return false

        return GLFW.glfwGetKey(mc.window.handle(), key.value) == GLFW.GLFW_PRESS
    }
}
