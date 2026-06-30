package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.width
import java.awt.Color
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ClockDisplay: Feature("Displays the system time on screen.") {
    private val seconds by ToggleSetting("Show Seconds", true)
    private val color by ColorSetting("Color", Color(255, 134, 0), false)

    override fun init() {
        hudElement("ClockDisplay") { ctx, _ ->
            val text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm${if (seconds.value) ":ss" else ""}"))
            Render2D.drawString(ctx, text, 0, 0, color.value)
            return@hudElement text.width().toFloat() to 9f
        }
    }
}