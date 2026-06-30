package com.renderoptimiser.ui.clickgui.components

import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.features.impl.dev.ClickGui
import com.renderoptimiser.utils.ColorUtils.withAlpha
import com.renderoptimiser.utils.MathUtils
import com.renderoptimiser.utils.NumbersUtils.div
import com.renderoptimiser.utils.NumbersUtils.minus
import com.renderoptimiser.utils.NumbersUtils.plus
import com.renderoptimiser.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import java.awt.Color

object Style {
    val accentColor get() = ClickGui.accentColor.value
    val accentColorTrans get() = accentColor.withAlpha(120)

    // Matches the ClickGui expanded sub-panel background so inline settings blend in.
    val bg = Color(22, 24, 29)

    // Slightly lighter than [bg] for slider/toggle tracks.
    private val trackColor = Color(47, 52, 64)

    fun drawBackground(ctx: GuiGraphicsExtractor, x: Number, y: Number, w: Number, h: Number) {
        Render2D.drawRect(ctx, x, y, w, h, bg)
    }

    fun drawHoverBar(ctx: GuiGraphicsExtractor, x: Number, y: Number, height: Number, anim: Float) {
        if (anim <= 0.01f) return
        val barH = (height - 6f) * anim
        val barY = y + (height / 2f) - (barH / 2f)
        Render2D.drawRect(ctx, x, barY, 2f, barH, accentColor.withAlpha((220 * anim).toInt()))
    }

    fun drawNudgedText(ctx: GuiGraphicsExtractor, text: String, x: Float, y: Float, anim: Float, color: Color = Color.WHITE) {
        val xOffset = 2f * anim
        Render2D.drawString(ctx, text, x + xOffset, y, color, 1, true)
    }

    fun drawSlider(ctx: GuiGraphicsExtractor, x: Float, y: Float, w: Float, progress: Float, hoverAnim: Float, color: Color) {
        val h = 3f
        Render2D.drawRect(ctx, x, y, w, h, trackColor)
        val barColor = MathUtils.lerpColor(Color(color.red, color.green, color.blue, 200), color, hoverAnim)
        Render2D.drawRect(ctx, x, y, w * progress, h, barColor)
        val kSize = 5f
        Render2D.drawRect(ctx, x + (w * progress) - (kSize / 2f), y + (h / 2f) - (kSize / 2f), kSize, kSize, Color.WHITE)
    }

    fun playClickSound(pitch: Float) {
        if (! ClickGui.playClickSound.value) return
        mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch))
    }
}