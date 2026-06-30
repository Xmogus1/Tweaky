package com.renderoptimiser.features.impl.dev

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.ClickGuiScreen
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.hud.HudEditorScreen
import java.awt.Color

object ClickGui: Feature("A feature used to change the ClickGui configuration.", toggled = true) {
    val playClickSound by ToggleSetting("Click Sound", true)
        .withDescription("Toggle for the sound that plays when you click on a setting element.")

    val accentColor by ColorSetting("Accent Color", Color(91, 140, 255), false)
        .withDescription("The accent color used by the whole ClickGui.")

    val panelSorting by DropdownSetting("Sorting", 1, listOf("A-Z Sorting", "Width Sorting", "No Sorting"))
        .withDescription("The order of the features in the panels.")

    val editGuiButton by ButtonSetting("Open HUD Editor") {
        ClickGuiScreen.onClose()
        RenderOptimiser.screen = HudEditorScreen
    }.withDescription("Opens the HUD Editor Screen where you can change you HUD elements size and position.")

    val resetButton by ButtonSetting("Reset Settings") {
        playClickSound.value = true
        accentColor.value = Color(91, 140, 255)
    }.withDescription("Reverts settings back to their original values.")

    override fun toggle() {}
}