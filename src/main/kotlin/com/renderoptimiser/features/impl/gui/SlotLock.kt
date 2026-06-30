package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.config.PogObject
import com.renderoptimiser.event.impl.ContainerEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.interfaces.IContainerScreen
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.ui.clickgui.components.Style
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.KeybindSetting
import com.renderoptimiser.utils.render.Render2D.highlight
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW
import java.awt.Color

/**
 * Lock inventory slots against misclicks: hover a slot in your inventory and press the lock key.
 * Locked slots ignore every interaction — clicks, shift-clicks, number-key/offhand swaps, Q-drops,
 * drag-crafting — because they all funnel through the one slotClicked hook
 * ([com.renderoptimiser.mixin.MixinAbstractContainerScreen] fires the cancellable SlotClick event).
 * Locks are per player-inventory slot index, work in ANY container screen, and persist to disk.
 */
object SlotLock: Feature("Lock inventory slots against misclicks.") {

    private val lockKey by KeybindSetting("Lock Key", GLFW.GLFW_KEY_L)
        .withDescription("Hover a slot in your inventory and press this to lock/unlock it.")

    private val lockColor by ColorSetting("Lock Color", Color(255, 60, 60, 110))

    /** Locked player-inventory container-slot indices (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand). */
    private val store = PogObject("slotLocks", mutableSetOf<Int>())
    private val locks get() = store.get()

    private fun isLocked(slot: Slot): Boolean =
        slot.container === mc.player?.inventory && slot.containerSlot in locks

    /**
     * Blocks the in-game Q-drop (no screen open) when the SELECTED hotbar slot is locked — called from
     * [com.renderoptimiser.mixin.MixinLocalPlayerDrop]. Container-screen drops are covered separately.
     */
    @JvmStatic
    fun shouldBlockDrop(): Boolean {
        if (!enabled) return false
        val selected = mc.player?.inventory?.selectedSlot ?: return false
        if (selected !in locks) return false
        NotificationManager.push("Slot locked", "Unlock it to drop items")
        return true
    }

    override fun init() {
        // Toggle lock on the hovered slot.
        register<ContainerEvent.Keyboard> {
            if (event.key != lockKey.value) return@register
            val hovered = (event.screen as? IContainerScreen)?.tweaky_getHoveredSlot() ?: return@register
            if (hovered.container !== mc.player?.inventory) return@register

            val index = hovered.containerSlot
            if (!locks.add(index)) locks.remove(index)
            store.save()
            Style.playClickSound(if (index in locks) 1.15f else 0.85f)
            event.isCanceled = true
        }

        // Block every interaction with a locked slot (and swaps TARGETING a locked hotbar/offhand slot).
        register<ContainerEvent.SlotClick> {
            val slot = event.slot
            if (slot != null && isLocked(slot)) {
                event.isCanceled = true
                return@register
            }
            if (event.clickType == ContainerInput.SWAP && event.button in locks) {
                event.isCanceled = true
            }
        }

        // Tint locked slots.
        register<ContainerEvent.Render.Slot.Post> {
            if (isLocked(event.slot)) event.slot.highlight(event.context, lockColor.value)
        }
    }
}
