package com.renderoptimiser.utils.render


import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object ModRenderLayers {
    val FILLED = RenderType.create("ro_filled", RenderSetup.builder(ModRenderPipelines.FILLED).createRenderSetup())
    val FILLED_THROUGH_WALLS = RenderType.create("ro_filled_through_walls", RenderSetup.builder(ModRenderPipelines.FILLED_THROUGH_WALLS).createRenderSetup())

    val CIRCLE_FILLED = RenderType.create("ro_circle_filled", RenderSetup.builder(ModRenderPipelines.CIRCLE_FILLED).createRenderSetup())
    val CIRCLE_FILLED_THROUGH_WALLS = RenderType.create("ro_circle_filled_through_walls", RenderSetup.builder(ModRenderPipelines.CIRCLE_FILLED_THROUGH_WALLS).createRenderSetup())

    val LINES = RenderType.create("ro_lines", RenderSetup.builder(ModRenderPipelines.LINES).createRenderSetup())
    val LINES_THROUGH_WALLS = RenderType.create("ro_lines_through_walls", RenderSetup.builder(ModRenderPipelines.LINES_THROUGH_WALLS).createRenderSetup())

    @JvmField val phaseLayers = setOf(FILLED_THROUGH_WALLS, CIRCLE_FILLED_THROUGH_WALLS, LINES_THROUGH_WALLS)
    @JvmField val afterTerrainLayers = setOf(FILLED, CIRCLE_FILLED, LINES)
}