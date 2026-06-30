package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.height
import com.renderoptimiser.utils.render.Render2D.width
import java.awt.Color

object FpsDisplay: Feature("Displays the game's FPS on screen.") {
    private val color by ColorSetting("Color", Color(230, 114, 230), false)

    override fun init() {
        hudElement("FpsDisplay") { ctx, _ ->
            val text = "${mc.fps} fps"
            Render2D.drawString(ctx, text, 0, 0, color.value)
            return@hudElement text.width().toFloat() to text.height().toFloat()
        }
    }
}