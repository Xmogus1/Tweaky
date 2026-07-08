package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.height
import com.renderoptimiser.utils.render.Render2D.width
import net.minecraft.core.Direction
import java.awt.Color

object CoordinatesHud: Feature("Shows your coordinates, direction & biome.") {

    private val color by ColorSetting("Color", Color(230, 230, 230), false)
    private val showDirection by ToggleSetting("Show Direction", true)
    private val showBiome by ToggleSetting("Show Biome", true)
    private val portalCoords by ToggleSetting("Portal Coordinates", false)
        .withDescription("Also show the matching Nether/Overworld coordinates.")

    override fun init() {
        hudElement("Coordinates") { ctx, _ ->
            val player = mc.player
            val level = mc.level
            val lines = ArrayList<String>(3)

            if (player == null || level == null) {
                lines.add("X: 0 Y: 64 Z: 0")
                if (showDirection.value || showBiome.value) lines.add("north | plains")
            }
            else {
                lines.add("X: ${player.blockX} Y: ${player.blockY} Z: ${player.blockZ}")

                var info = ""
                if (showDirection.value) {
                    info = Direction.fromYRot(player.yRot.toDouble()).name.lowercase()
                }
                if (showBiome.value) {
                    val biome = level.getBiome(player.blockPosition()).unwrapKey()
                        .map { it.identifier().path.replace('_', ' ') }
                        .orElse("unknown")
                    info = if (info.isEmpty()) biome else "$info | $biome"
                }
                if (info.isNotEmpty()) lines.add(info)

                if (portalCoords.value) {
                    val scale = level.dimensionType().coordinateScale()
                    lines.add(
                        if (scale > 1.0) "Overworld: ${(player.x * scale).toInt()}, ${(player.z * scale).toInt()}"
                        else "Nether: ${(player.x / 8).toInt()}, ${(player.z / 8).toInt()}"
                    )
                }
            }

            var y = 0f
            var w = 0f
            lines.forEach { line ->
                Render2D.drawString(ctx, line, 0, y.toInt(), color.value)
                w = maxOf(w, line.width().toFloat())
                y += line.height().toFloat() + 2f
            }
            return@hudElement w to (y - 2f).coerceAtLeast(0f)
        }
    }
}
