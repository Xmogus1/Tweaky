package com.renderoptimiser.features.impl.dev

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.config.Config
import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.FeatureManager
import com.renderoptimiser.ui.clickgui.ClickGuiScreen
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.KeybindSetting
import com.renderoptimiser.ui.clickgui.components.impl.MultiCheckboxSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.hud.HudEditorScreen
import com.renderoptimiser.ui.notification.NotificationManager
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

    private var resetArmedAt = 0L

    val resetButton by ButtonSetting("Reset All Settings") {
        val now = System.currentTimeMillis()
        if (now - resetArmedAt > 3000L) {
            // First click only arms the reset — a second click within 3s confirms.
            resetArmedAt = now
            NotificationManager.error("Reset ALL settings?", "Click again within 3s to confirm")
        }
        else {
            resetArmedAt = 0L

            FeatureManager.features.forEach { feature ->
                feature.configSettings.forEach { setting ->
                    when (setting) {
                        is MultiCheckboxSetting -> setting.resetToDefaults()
                        is KeybindSetting -> {
                            setting.reset()
                            setting.isMouse = false   // reset() restores the key code, not the mouse flag
                        }
                        else -> setting.reset()
                    }
                }
                // Restore each feature's built-in default enabled state (toggle handles listeners).
                if (feature.enabled != feature.defaultEnabled) feature.toggle()
            }

            Config.save()
            NotificationManager.push("Settings reset", "Every feature restored to defaults")
        }
    }.withDescription("Resets EVERY feature and setting in the mod to its defaults. Click twice to confirm.")

    override fun toggle() {}
}