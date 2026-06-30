package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.utils.render.MapDecorationSkin
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import kotlin.math.abs

/**
 * Replaces the player arrows on filled maps (held + item frames) with the matching player's head.
 *
 * Identity problem: the client re-keys map decorations as "icon-N" and player markers carry no name, so
 * we match each PLAYER decoration to an in-render-distance player by reproducing the server's
 * world-position -> map-pixel math (MapItemSavedData.addDecoration) and picking the closest player within
 * a small tolerance. Matched decorations get the player's skin stashed on the render state via
 * [MapDecorationSkin]; [com.renderoptimiser.mixin.MixinMapRenderer] then draws the head instead of the
 * arrow. Unmatched arrows (players out of render distance, off-map markers) stay vanilla.
 */
object MapPlayerHeads: Feature("Shows player heads instead of arrows on maps.", toggled = true) {

    /**
     * Called from MixinMapRenderer at the TAIL of extractRenderState, where the saved data (world-space
     * info) and the freshly extracted render state (decoration list, same order) are both available.
     */
    @JvmStatic
    fun assignSkins(savedData: MapItemSavedData, state: MapRenderState) {
        if (!enabled) return
        val level = mc.level ?: return
        val players = level.players()
        if (players.isEmpty()) return

        val blocksPerPixel = 1 shl savedData.scale.toInt()
        val decorations = savedData.decorations.iterator()

        for (rs in state.decorations) {
            if (!decorations.hasNext()) break
            val dec = decorations.next()
            if (!dec.type().`is`(MapDecorationTypes.PLAYER)) continue

            // Reproduce the server's map-pixel math for each visible player; closest within tolerance wins.
            var best: AbstractClientPlayer? = null
            var bestScore = 7 // ~3 map pixels per axis of slack (one server tick of movement lag)
            for (p in players) {
                val fx = ((p.x - savedData.centerX) / blocksPerPixel).toFloat()
                val fz = ((p.z - savedData.centerZ) / blocksPerPixel).toFloat()
                if (abs(fx) >= 63f || abs(fz) >= 63f) continue
                val score = abs((fx * 2f + 0.5f).toInt() - dec.x()) + abs((fz * 2f + 0.5f).toInt() - dec.y())
                if (score < bestScore) {
                    bestScore = score
                    best = p
                }
            }

            if (best != null) {
                (rs as MapDecorationSkin).tweaky_skin = best.skin.body().texturePath()
            }
        }
    }
}
