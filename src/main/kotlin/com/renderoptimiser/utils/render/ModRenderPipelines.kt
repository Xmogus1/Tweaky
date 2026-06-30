package com.renderoptimiser.utils.render

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.utils.render.iris.IrisCompatibility
import com.renderoptimiser.utils.render.iris.IrisShaderType
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.*

object ModRenderPipelines {
    val FILLED: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(id("pipeline/filled"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
    )

    val CIRCLE_FILLED: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(id("pipeline/circle_filled"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .build()
    )

    val LINES_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(id("pipeline/lines_through_walls"))
            .withDepthStencilState(Optional.empty())
            .build()
    )

    val LINES: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(id("pipeline/lines"))
            .build()
    )

    val FILLED_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(id("pipeline/filled_through_walls"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(Optional.empty())
            .build()
    )

    val CIRCLE_FILLED_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(id("pipeline/circle_filled_through_walls"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .withDepthStencilState(Optional.empty())
            .build()
    )

    fun init() {
        IrisCompatibility.registerPipeline(LINES_THROUGH_WALLS, IrisShaderType.LINES)
        IrisCompatibility.registerPipeline(FILLED_THROUGH_WALLS, IrisShaderType.BASIC)
        IrisCompatibility.registerPipeline(CIRCLE_FILLED_THROUGH_WALLS, IrisShaderType.BASIC)
    }

    private fun id(path: String) = Identifier.fromNamespaceAndPath(RenderOptimiser.MOD_ID, path)
}