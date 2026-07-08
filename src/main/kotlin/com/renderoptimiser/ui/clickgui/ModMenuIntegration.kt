package com.renderoptimiser.ui.clickgui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/**
 * @see resources/fabric.mod.json5
 */
@Suppress("unused")
class ModMenuIntegration: ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            // Esc/close returns to ModMenu's mod list instead of closing to nothing
            ClickGuiScreen.modMenuParent = parent
            ClickGuiScreen
        }
    }
}