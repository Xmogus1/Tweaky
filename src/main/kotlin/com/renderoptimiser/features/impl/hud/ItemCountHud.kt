package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.height
import com.renderoptimiser.utils.render.Render2D.width
import net.minecraft.world.item.ItemStack
import java.awt.Color

/** Shows how many of the currently held item you carry across your whole inventory. */
object ItemCountHud: Feature("Shows how many of the held item you carry.") {

    private val color by ColorSetting("Color", Color(230, 230, 230), false)

    override fun init() {
        hudElement("ItemCount", shouldDraw = { mc.player?.mainHandItem?.isEmpty == false }) { ctx, _ ->
            val held = mc.player?.mainHandItem
            val text = if (held == null || held.isEmpty) "64 x Item"
            else {
                val inventory = mc.player!!.inventory
                var total = 0
                for (i in 0 until inventory.containerSize) {
                    val stack = inventory.getItem(i)
                    if (ItemStack.isSameItem(stack, held)) total += stack.count
                }
                "$total x ${held.hoverName.string}"
            }

            Render2D.drawString(ctx, text, 0, 0, color.value)
            return@hudElement text.width().toFloat() to text.height().toFloat()
        }
    }
}
