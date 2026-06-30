package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting

/**
 * @see com.renderoptimiser.mixin.MixinAbstractRecipeBookScreen
 * @see com.renderoptimiser.mixin.MixinRecipeBookComponent
 */
object HideRecipeBook: Feature("Hides the recipe book button in inventory GUIs.") {
    @JvmStatic val closeRecipeBook by ToggleSetting("Close Recipe Book").withDescription("Also closes the recipe book screen.")
}