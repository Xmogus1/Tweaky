package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import kotlin.math.sqrt

/**
 * Client-side dynamic lighting: held torches / lanterns / glowstone (and burning mobs, dropped glowing
 * items) illuminate their surroundings.
 *
 * Architecture (LambDynamicLights-style, no light-engine surgery):
 *  - Every tick, scan rendered entities for light emitters and publish an immutable snapshot of
 *    (x, y, z, luminance) sources. The snapshot is @Volatile because terrain meshing samples it from
 *    worker threads.
 *  - [getLightLevel] is the thread-safe lookup: distance-falloff light from the nearest source.
 *  - Two mixins merge it into vanilla's packed light at RETURN:
 *    entity light -> MixinEntityRendererLight (EntityRenderer.getPackedLightCoords, both versions);
 *    terrain/particles/fluids -> MixinDynamicLightTerrain (26.1.2: LevelRenderer.getLightCoords,
 *    26.2: LightCoordsUtil.getLightCoords — the method moved; that mixin is a DIVERGENT file).
 *  - Terrain only re-lights when its section rebuilds, so each tick we mark the sections around sources
 *    that appeared / moved / changed / vanished dirty ([ClientLevel.setSectionDirtyWithNeighbors] is
 *    public and identical in both versions).
 */
object DynamicLights: Feature("Held torches and glowing items light up their surroundings.", toggled = true) {

    /**
     * Quality ladder — index into [UPDATE_INTERVALS] and the scan/falloff tiers:
     *  Fastest   every 16 ticks, only YOUR held light, box-shaped falloff (cheapest)
     *  Fast      every 8 ticks, players only, box falloff
     *  Medium    every 4 ticks, all entities, round falloff
     *  High      every 2 ticks, all entities, round falloff
     *  Very High every tick, all entities, round falloff
     *  Fabulous  every tick + re-lights terrain around sources every tick even when idle (most accurate)
     */
    private val quality by DropdownSetting("Quality", 3, listOf("Fastest", "Fast", "Medium", "High", "Very High", "Fabulous"))
        .withDescription("Update rate, which entities emit, and the light shape. Higher = smoother, heavier.")

    private val heldItems by ToggleSetting("Held Items", true)
        .withDescription("Light from items held by players and mobs.")

    private val droppedItems by ToggleSetting("Dropped Items", true)
        .withDescription("Light from glowing items lying on the ground.")

    private val burningEntities by ToggleSetting("Burning Entities", true)
        .withDescription("Burning mobs and players glow.")

    /** Immutable (x, y, z, luminance) quads — @Volatile: chunk meshing reads it off-thread. */
    @Volatile
    private var sources = IntArray(0)

    /** Box-shaped (Chebyshev) falloff on the low quality tiers; round (Euclidean) otherwise. */
    @Volatile
    private var boxFalloff = false

    /** entity id -> (packed BlockPos, luminance) from the previous update, for section dirtying. */
    private val lastTick = HashMap<Int, Pair<Long, Int>>()

    /** Ticks between light updates, per quality tier. */
    private val UPDATE_INTERVALS = intArrayOf(16, 8, 4, 2, 1, 1)
    private var tickCounter = 0

    init {
        register<TickEvent.Start> { update() }
    }

    override fun onDisable() {
        super.onDisable()
        // Extinguish: publish empty, then rebuild the sections that were lit so the light disappears.
        sources = IntArray(0)
        lastTick.values.forEach { dirtyAround(BlockPos.of(it.first)) }
        lastTick.clear()
    }

    private fun update() {
        val level = mc.level
        if (level == null) {
            sources = IntArray(0)
            lastTick.clear()
            return
        }

        val q = quality.value.coerceIn(0, 5)
        if (++tickCounter < UPDATE_INTERVALS[q]) return
        tickCounter = 0
        boxFalloff = q <= 1

        // Scan scope grows with quality: just you -> players -> every rendered entity.
        val scanned: Iterable<Entity> = when {
            q == 0 -> listOfNotNull(mc.player)
            q == 1 -> level.players()
            else -> level.entitiesForRendering()
        }

        val out = ArrayList<Int>(8)
        val now = HashMap<Int, Pair<Long, Int>>()

        for (entity in scanned) {
            val lum = luminance(entity)
            if (lum <= 0) continue
            val bp = entity.blockPosition()
            out.add(bp.x); out.add(bp.y); out.add(bp.z); out.add(lum)
            now[entity.id] = bp.asLong() to lum
        }

        // Rebuild terrain around sources that appeared, moved, changed brightness, or vanished.
        for ((id, cur) in now) {
            val old = lastTick.remove(id)
            if (old == null || old != cur) {
                dirtyAround(BlockPos.of(cur.first))
                if (old != null && old.first != cur.first) dirtyAround(BlockPos.of(old.first))
            }
        }
        lastTick.values.forEach { dirtyAround(BlockPos.of(it.first)) } // sources that vanished
        lastTick.clear()
        lastTick.putAll(now)

        // Fabulous: re-light around every source every tick, even standing still (block updates,
        // overlapping lights, section-border edge cases) — most accurate, heaviest.
        if (q >= 5) now.values.forEach { dirtyAround(BlockPos.of(it.first)) }

        sources = out.toIntArray()
    }

    private fun dirtyAround(pos: BlockPos) {
        mc.level?.setSectionDirtyWithNeighbors(
            SectionPos.blockToSectionCoord(pos.x),
            SectionPos.blockToSectionCoord(pos.y),
            SectionPos.blockToSectionCoord(pos.z)
        )
    }

    private fun luminance(entity: Entity): Int {
        var lum = 0
        if (burningEntities.value && entity.isOnFire) lum = 15
        if (lum < 15) when (entity) {
            is LivingEntity ->
                if (heldItems.value) lum = maxOf(lum, itemLuminance(entity.mainHandItem), itemLuminance(entity.offhandItem))
            is ItemEntity ->
                if (droppedItems.value) lum = maxOf(lum, itemLuminance(entity.item))
            else -> {}
        }
        return lum
    }

    private fun itemLuminance(stack: ItemStack): Int {
        if (stack.isEmpty) return 0
        if (stack.item === Items.LAVA_BUCKET) return 15
        return Block.byItem(stack.item).defaultBlockState().lightEmission
    }

    /**
     * Thread-safe dynamic block-light level at [pos] (0..15), with linear distance falloff. Called from
     * the light mixins on the render AND meshing threads — must stay allocation-free and fast (squared
     * distance early-reject before the sqrt).
     */
    @JvmStatic
    fun getLightLevel(pos: BlockPos): Int {
        val s = sources
        if (s.isEmpty()) return 0
        val box = boxFalloff
        val px = pos.x
        val py = pos.y
        val pz = pos.z
        var best = 0
        var i = 0
        while (i < s.size) {
            val lum = s[i + 3]
            val dx = px - s[i]
            val dy = py - s[i + 1]
            val dz = pz - s[i + 2]
            if (box) {
                // Chebyshev distance: box-shaped light, no sqrt (low quality tiers).
                var d = if (dx < 0) -dx else dx
                val ay = if (dy < 0) -dy else dy
                val az = if (dz < 0) -dz else dz
                if (ay > d) d = ay
                if (az > d) d = az
                if (d < lum) {
                    val v = lum - d
                    if (v > best) best = v
                }
            }
            else {
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq < lum * lum) {
                    val v = lum - sqrt(distSq.toDouble()).toInt()
                    if (v > best) best = v
                }
            }
            i += 4
        }
        return if (best > 15) 15 else best
    }
}
