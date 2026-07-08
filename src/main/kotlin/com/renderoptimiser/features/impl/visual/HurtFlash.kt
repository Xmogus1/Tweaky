package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.mixin.IOverlayTexture
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import java.awt.Color

/**
 * Recolors the red damage flash on entities. Vanilla bakes the flash into a 16x16 overlay texture at
 * startup (rows V=0..7 = hurt band, ARGB 0xB2FF0000); we repaint those rows with the chosen colour +
 * opacity and re-upload. Applied lazily on the next tick (the texture may not exist at mod-init time);
 * disabling restores the exact vanilla pixels.
 */
object HurtFlash: Feature("Customize the red damage flash on entities.") {

    private const val VANILLA_ARGB = 0xB2FF0000.toInt()

    private val color by ColorSetting("Color", Color(255, 0, 0), false)
        .onChange { dirty = true }

    private val opacity by SliderSetting("Opacity", 70, 5, 100, 1, "%")
        .withDescription("Strength of the flash. Vanilla is 70%.")
        .onChange { dirty = true }

    private var dirty = true

    override fun init() {
        register<TickEvent.Start> {
            if (dirty) {
                dirty = false
                runCatching { paint(customArgb()) }
            }
        }
    }

    override fun onEnable() {
        super.onEnable()
        dirty = true
    }

    override fun onDisable() {
        super.onDisable()
        // Toggled from the ClickGui (render thread) — restore the vanilla red immediately.
        runCatching { paint(VANILLA_ARGB) }
        dirty = false
    }

    private fun customArgb(): Int {
        val alpha = (opacity.value * 255 / 100).coerceIn(0, 255)
        return (alpha shl 24) or (color.value.rgb and 0xFFFFFF)
    }

    /** Repaints the hurt band (rows 0..7) of the overlay texture and re-uploads it. */
    private fun paint(argb: Int) {
        val texture = (mc.gameRenderer.overlayTexture() as IOverlayTexture).texture
        val image = texture.pixels ?: return
        for (y in 0..7) {
            for (x in 0..15) {
                image.setPixel(x, y, argb)
            }
        }
        texture.upload()
    }
}
