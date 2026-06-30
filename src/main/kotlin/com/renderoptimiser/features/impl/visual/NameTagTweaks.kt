package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting

object NameTagTweaks: Feature(name = "Nametag Tweaks") {
    @JvmStatic
    val disableNametagBackground by ToggleSetting("Hide Nametag Background").withDescription("Disable Nametag's black background.")

    @JvmStatic
    val addNameTagTextShadow by ToggleSetting("Shadowed Nametag").withDescription("Adds a text shadow to the nametag label.")
}