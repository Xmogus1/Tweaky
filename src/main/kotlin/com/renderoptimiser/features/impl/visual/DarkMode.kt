package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.utils.ColorUtils.withAlpha
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color

/**
 * @see com.renderoptimiser.mixin.MixinHud.onRenderHudPre
 * @see com.renderoptimiser.mixin.MixinHud.onRenderHudPost
 */
object DarkMode: Feature("Darkens the screen") {
    private val opacity by SliderSetting("Opacity", 25, 1, 100, 1).withDescription("The strength of the dark tint.")

    @JvmStatic
    val tintHud by ToggleSetting("Tint HUD").withDescription("Should the dark tint also apply to HUD elements?")

    @JvmStatic
    fun drawOverlay(ctx: GuiGraphicsExtractor) {
        if (! enabled) return
        val window = mc.window
        ctx.fill(
            0, 0,
            window.guiScaledWidth,
            window.guiScaledHeight,
            Color.BLACK.withAlpha(opacity.value / 100f).rgb
        )
    }
}