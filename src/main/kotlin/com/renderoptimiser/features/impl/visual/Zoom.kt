package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.KeybindSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs
import kotlin.math.min

/**
 * Fully-configurable hold-to-zoom.
 *
 * Zoom is expressed as a "level": 1.0 = no zoom (FOV unchanged), higher = more zoom. The FOV multiplier
 * is applied in [com.renderoptimiser.mixin.MixinCamera]'s calculateFov hook (`1.0 / level`). The mouse
 * wheel adjusts the level while the key is held via [com.renderoptimiser.mixin.MixinMouseHandlerZoom]
 * routing to [onScroll].
 *
 * The FOV/scroll paths are driven directly by the two mixins (each a no-op while disabled or not held); the
 * spyglass scope overlay is drawn from the HUD mixin's post hook ([renderScope]), which also hides the rest
 * of the HUD while zooming. All state is touched only on the client (render) thread — no synchronisation.
 */
object Zoom: Feature("Hold a key to zoom in; scroll to adjust. Fully configurable.", toggled = true) {

    val zoomKey by KeybindSetting("Zoom Key", GLFW.GLFW_KEY_C)
        .withDescription("Hold this key to zoom in.")

    private val startLevel by SliderSetting("Start Zoom Level", 4.0, 1.0, 50.0, 0.5)
        .withDescription("The zoom level applied the moment you press the key.")

    private val maxLevel by SliderSetting("Max Zoom Level", 20.0, 1.0, 50.0, 0.5)
        .withDescription("The furthest you can zoom in by scrolling.")

    private val scrollSpeed by SliderSetting("Scroll Speed", 1.0, 0.1, 10.0, 0.1)
        .withDescription("How much each mouse-wheel notch changes the zoom level.")

    private val smooth by ToggleSetting("Smooth Zoom", true)
        .withDescription("Ease the zoom in and out instead of snapping instantly.")

    private val skyglass by ToggleSetting("Skyglass Effect", true)
        .withDescription("Show the spyglass scope overlay and play the spyglass sound while zooming.")

    // ---- runtime state (client thread only; level units, 1.0 == fully out) ----
    private var targetLevel = 1.0
    private var currentLevel = 1.0
    private var wasHeld = false
    private var lastNanos = 0L

    /** Exponential-approach rate for smooth zoom (fraction of remaining distance closed per second). */
    private const val SMOOTH_PER_SEC = 14.0

    /** Vanilla spyglass scope texture, reused for the "skyglass" scope overlay. */
    private val scopeTexture = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/spyglass_scope.png")

    /** enabled + in-world (mouse grabbed, player present) + zoom key physically held. */
    private fun held(): Boolean =
        enabled && mc.player != null && mc.mouseHandler.isMouseGrabbed() && zoomKey.isDown()

    /**
     * Called every rendered frame from the FOV mixin. Handles the activation edge (spyglass sound + reset
     * to the start level), advances the smooth interpolation using real elapsed time (frame-rate
     * independent and safe to call multiple times per frame), and returns the FOV multiplier (1.0 = no
     * change). A no-op returning 1.0f whenever the feature is off or the key is not held.
     */
    @JvmStatic
    fun fovMultiplier(): Float {
        val keyHeld = enabled && zoomKey.isDown()
        val inWorld = mc.player != null && mc.mouseHandler.isMouseGrabbed()

        // A zoom "session" starts on a genuine key press (raw key edge) and lasts until the key is
        // released. Opening a menu mid-session only pauses the visual zoom (inWorld=false); it does NOT
        // reset the level or replay the sound, so closing the menu resumes at the same zoom.
        if (keyHeld && !wasHeld) {
            targetLevel = startLevel.value.coerceIn(1.0, maxLevel.value)
            if (inWorld && skyglass.value) {
                runCatching { mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_USE, 1.0f, 1.0f)) }
            }
        }
        wasHeld = keyHeld

        val target = if (keyHeld && inWorld) targetLevel.coerceIn(1.0, maxLevel.value) else 1.0

        if (smooth.value) {
            val now = System.nanoTime()
            // Cap dt so a long stall (unfocused window / lag spike) can't snap the zoom through in one frame.
            val dt = if (lastNanos == 0L) 0.0 else ((now - lastNanos) / 1_000_000_000.0).coerceAtMost(0.1)
            lastNanos = now
            currentLevel += (target - currentLevel) * (dt * SMOOTH_PER_SEC).coerceIn(0.0, 1.0)
            if (abs(currentLevel - target) < 1e-3) currentLevel = target
        }
        else {
            currentLevel = target
            lastNanos = 0L
        }

        return if (currentLevel <= 1.0001) 1.0f else (1.0 / currentLevel).toFloat()
    }

    /**
     * Called from the scroll mixin. Returns true (consuming the wheel, so vanilla hotbar scroll is
     * cancelled) only while actively zooming, stepping the target level by [scrollSpeed] within
     * `[1.0, maxLevel]`. When not zooming it returns false and vanilla scrolling is untouched.
     */
    @JvmStatic
    fun onScroll(vertical: Double): Boolean {
        if (!held() || vertical == 0.0) return false
        // Scale by the raw delta so a mouse-wheel notch (+/-1.0) = +/-scrollSpeed while a trackpad's
        // fractional deltas adjust proportionally instead of jumping a full step per flick.
        targetLevel = (targetLevel + vertical * scrollSpeed.value).coerceIn(1.0, maxLevel.value)
        return true
    }

    /** Scope opacity, ramped with the current zoom so it fades in/out with the FOV. */
    private fun scopeAlpha(): Float = ((currentLevel - 1.0) * 4.0).coerceIn(0.0, 1.0).toFloat()

    /**
     * True only while the zoom key is actively held in-world and the effect is on. The per-version HUD
     * mixin uses this to hide the rest of the HUD, and [renderScope] to draw the scope — so BOTH stop the
     * instant the key is released (the HUD returns immediately), while the FOV still eases back smoothly.
     */
    @JvmStatic
    fun isScopeActive(): Boolean = skyglass.value && held()

    /**
     * Draws the vanilla spyglass scope (centred square texture + black bars for the rest of the screen)
     * over the world while zooming. Called every HUD frame from the HUD mixin's post hook
     * (MixinGui/MixinHud#onRenderHudPost). Alpha follows [scopeAlpha] so it fades with the zoom.
     */
    @JvmStatic
    fun renderScope(ctx: GuiGraphicsExtractor) {
        if (!isScopeActive()) return

        val alpha = (scopeAlpha() * 255f).toInt().coerceIn(0, 255)
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val size = min(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2

        Render2D.drawTexture(ctx, scopeTexture, x, y, size, size, Color(255, 255, 255, alpha))

        // Black bars for the regions the square scope does not cover.
        val black = Color(0, 0, 0, alpha).rgb
        if (y > 0) {
            ctx.fill(0, 0, w, y, black)
            ctx.fill(0, y + size, w, h, black)
        }
        if (x > 0) {
            ctx.fill(0, y, x, y + size, black)
            ctx.fill(x + size, y, w, y + size, black)
        }
    }
}
