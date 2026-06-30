package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.utils.NumbersUtils.toFixed
import com.renderoptimiser.utils.ServerUtils
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.height
import com.renderoptimiser.utils.render.Render2D.width
import java.awt.Color

object TpsDisplay: Feature("Displays the Server's Ticks Per Second (TPS) on screen.") {
    private val color by ColorSetting("Color", Color(0, 114, 255), false)

    override fun init() {
        hudElement("TpsDisplay") { ctx, example ->
            val text = "TPS: &f${if (example) 20 else ServerUtils.tps.toFixed(1)}"
            Render2D.drawString(ctx, text, 0, 0, color.value)
            return@hudElement text.width().toFloat() to text.height().toFloat()
        }
    }
}