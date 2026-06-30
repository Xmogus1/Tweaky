package com.renderoptimiser.utils.render

import com.renderoptimiser.RenderOptimiser.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object RenderHelper {
    val partialTicks get() = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

    val Entity.renderX get() = xo + (x - xo) * partialTicks
    val Entity.renderY get() = yo + (y - yo) * partialTicks
    val Entity.renderZ get() = zo + (z - zo) * partialTicks

    val Entity.renderVec get() = Vec3(renderX, renderY, renderZ)

    val Entity.renderBoundingBox get() = boundingBox.move(renderX - x, renderY - y, renderZ - z)
}