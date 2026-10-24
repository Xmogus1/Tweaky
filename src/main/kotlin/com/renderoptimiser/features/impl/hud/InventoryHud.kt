package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.hud.HudElement
import com.renderoptimiser.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.awt.Color

/**
 * Shows your inventory, armor and potion effects on the HUD. One movable HUD element (drag it in
 * /tweaky hud); heavily customizable - parts, sizes, orientation, backgrounds, slot colors, grid
 * outlines, item counts and effect options.
 */
object InventoryHud: Feature("Shows your inventory, armor and effects on the HUD. Heavily customizable.") {

    // parts
    private val showInventory by ToggleSetting("Inventory", true).withDescription("The 27 main inventory slots.")
    private val showHotbar by ToggleSetting("Hotbar", false).withDescription("Also show your 9 hotbar slots.")
    private val showArmor by ToggleSetting("Armor", true).withDescription("Your 4 armor pieces.")
    private val showOffhand by ToggleSetting("Offhand", true).withDescription("Include the offhand with the armor.")
    private val showEffects by ToggleSetting("Effects", true).withDescription("Active potion effects as text.")

    // items
    private val itemCounts by ToggleSetting("Item Counts", true).withDescription("Show stack sizes on items.")
    private val hideEmpty by ToggleSetting("Hide Empty Slots", false).withDescription("Don't draw empty slots at all.")

    // layout
    private val slotSize by SliderSetting("Slot Size", 18, 14, 28, 1).withDescription("Size of each slot box.")
    private val spacing by SliderSetting("Spacing", 0, 0, 8, 1).withDescription("Gap between slots.")
    private val scale by SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1).withDescription("Overall size of the HUD.")
    private val armorDir by DropdownSetting("Armor Direction", 0, listOf("Horizontal", "Vertical"))

    // background
    private val background by ToggleSetting("Background", true).withDescription("Draw a panel behind each part.")
    private val backgroundColor by ColorSetting("Background Color", Color(0, 0, 0, 140), true).showIf { background.value }

    // slots
    private val slotBackground by ToggleSetting("Slot Background", true).withDescription("Fill each slot box.")
    private val slotColor by ColorSetting("Slot Color", Color(255, 255, 255, 40), true).showIf { slotBackground.value }

    // outlines / grid lines
    private val outlines by ToggleSetting("Outlines", false).withDescription("Draw lines around each slot.")
    private val outlineColor by ColorSetting("Outline Color", Color(255, 255, 255, 120), true).showIf { outlines.value }

    // effects
    private val effectLevel by ToggleSetting("Effect Level", true).showIf { showEffects.value }
    private val effectDuration by ToggleSetting("Effect Duration", true).showIf { showEffects.value }
    private val effectColor by ColorSetting("Effect Color", Color(235, 235, 235), false).showIf { showEffects.value }

    private var hud: HudElement? = null

    override fun init() {
        hud = hudElement("Inventory HUD", shouldDraw = { mc.player != null }) { ctx, _ ->
            hud?.scale = scale.value.toFloat()
            val player = mc.player ?: return@hudElement 0f to 0f
            val size = slotSize.value.toInt()
            val gap = spacing.value.toInt()
            var oy = 0
            var maxW = 0f

            if (showInventory.value) {
                val (w, h) = grid(ctx, oy, 9, 3, player.inventory.nonEquipmentItems, 9, size, gap)
                maxW = maxOf(maxW, w); oy += h.toInt() + 3
            }
            if (showHotbar.value) {
                val (w, h) = grid(ctx, oy, 9, 1, player.inventory.nonEquipmentItems, 0, size, gap)
                maxW = maxOf(maxW, w); oy += h.toInt() + 3
            }
            if (showArmor.value) {
                val (w, h) = armorSection(ctx, oy, player, size, gap)
                maxW = maxOf(maxW, w); oy += h.toInt() + 3
            }
            if (showEffects.value) {
                val h = effectsSection(ctx, oy, player)
                maxW = maxOf(maxW, 9f * size); oy += h
            }

            maxW to oy.toFloat()
        }
    }

    private fun grid(ctx: GuiGraphicsExtractor, oy: Int, cols: Int, rows: Int, items: List<ItemStack>, start: Int, size: Int, gap: Int): Pair<Float, Float> {
        val step = size + gap
        val w = cols * size + (cols - 1) * gap
        val h = rows * size + (rows - 1) * gap
        if (background.value) Render2D.drawRect(ctx, - 2, oy - 2, w + 4, h + 4, backgroundColor.value)
        for (row in 0 until rows) for (col in 0 until cols) {
            drawSlot(ctx, col * step, oy + row * step, size, items.getOrNull(start + row * cols + col) ?: ItemStack.EMPTY)
        }
        return w.toFloat() to h.toFloat()
    }

    private fun armorSection(ctx: GuiGraphicsExtractor, oy: Int, player: Player, size: Int, gap: Int): Pair<Float, Float> {
        val stacks = armorStacks(player)
        val step = size + gap
        val horizontal = armorDir.value == 0
        val cols = if (horizontal) stacks.size else 1
        val rows = if (horizontal) 1 else stacks.size
        val w = cols * size + (cols - 1) * gap
        val h = rows * size + (rows - 1) * gap
        if (background.value) Render2D.drawRect(ctx, - 2, oy - 2, w + 4, h + 4, backgroundColor.value)
        for ((i, stack) in stacks.withIndex()) {
            val cx = if (horizontal) i * step else 0
            val cy = oy + if (horizontal) 0 else i * step
            drawSlot(ctx, cx, cy, size, stack)
        }
        return w.toFloat() to h.toFloat()
    }

    private fun drawSlot(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, size: Int, stack: ItemStack) {
        val empty = stack.isEmpty
        if (empty && hideEmpty.value) return
        if (slotBackground.value) Render2D.drawRect(ctx, cx, cy, size, size, slotColor.value)
        if (outlines.value) {
            val c = outlineColor.value
            Render2D.drawRect(ctx, cx, cy, size, 1, c)
            Render2D.drawRect(ctx, cx, cy + size - 1, size, 1, c)
            Render2D.drawRect(ctx, cx, cy, 1, size, c)
            Render2D.drawRect(ctx, cx + size - 1, cy, 1, size, c)
        }
        if (empty) return
        val off = (size - 16) / 2
        ctx.item(stack, cx + off, cy + off)
        if (itemCounts.value) ctx.itemDecorations(mc.font, stack, cx + off, cy + off)
    }

    private fun effectsSection(ctx: GuiGraphicsExtractor, oy: Int, player: Player): Int {
        val effects = player.activeEffects
        if (effects.isEmpty()) return 0
        var ey = oy
        for (effect in effects) {
            val name = runCatching { effect.effect.value().displayName.string }.getOrDefault("Effect")
            val lvl = effect.amplifier + 1
            val label = buildString {
                append(name)
                if (effectLevel.value && lvl > 1) append(" ").append(lvl)
                if (effectDuration.value) {
                    val time = if (effect.isInfiniteDuration) "**:**" else {
                        val s = effect.duration / 20
                        "%d:%02d".format(s / 60, s % 60)
                    }
                    append("  ").append(time)
                }
            }
            Render2D.drawString(ctx, label, 0, ey, effectColor.value)
            ey += 10
        }
        return ey - oy
    }

    private fun armorStacks(player: Player): List<ItemStack> {
        val list = mutableListOf(
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET),
        )
        if (showOffhand.value) list.add(player.getItemBySlot(EquipmentSlot.OFFHAND))
        return list
    }
}
