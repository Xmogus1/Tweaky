package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.MultiCheckboxSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.random.Random

/**
 * Particle reduction: category percentage sliders + a checkbox for EVERY registered particle type
 * (untick = that particle never spawns). Hooks BOTH spawn pipelines:
 *  - [shouldDrop] from MixinParticleEngine.createParticle — the normal spawn funnel.
 *  - [shouldDropBlockEffect] from MixinClientLevelParticles — block-break crumbs & hit cracks
 *    (addDestroyBlockEffect/addBreakingBlockEffect) construct particles DIRECTLY via ParticleEngine.add
 *    and never pass createParticle, which is why they need their own hooks.
 */
object ParticleControl: Feature("Reduce or disable particles by category or type.") {

    private val explosions by SliderSetting("Explosions", 100, 0, 100, 5, "%")
    private val smoke by SliderSetting("Smoke", 100, 0, 100, 5, "%")
        .withDescription("Campfires, torches, fires, furnaces.")
    private val potionEffects by SliderSetting("Potion Effects", 100, 0, 100, 5, "%")
        .withDescription("Effect swirls from potions and beacons.")
    private val blockDust by SliderSetting("Block Dust", 100, 0, 100, 5, "%")
        .withDescription("Block breaking/hitting crumbs and falling-block dust.")
    private val everythingElse by SliderSetting("Everything Else", 100, 0, 100, 5, "%")

    /** One checkbox per registered particle type (ticked = allowed). Untick to disable it completely. */
    private val particles by MultiCheckboxSetting(
        "Particles",
        BuiltInRegistries.PARTICLE_TYPE.keySet()
            .map { it.path }
            .sorted()
            .associateWithTo(LinkedHashMap()) { true }
    ).withDescription("Every particle in the game. Untick one to disable it entirely.")

    private fun allowed(type: ParticleType<*>): Boolean {
        val key = BuiltInRegistries.PARTICLE_TYPE.getKey(type) ?: return true
        return particles.value[key.path] != false
    }

    private fun roll(keepPercent: Int): Boolean {
        if (keepPercent >= 100) return false
        if (keepPercent <= 0) return true
        return Random.nextInt(100) >= keepPercent
    }

    /** Normal pipeline (ParticleEngine.createParticle). True = drop this spawn. */
    @JvmStatic
    fun shouldDrop(options: ParticleOptions): Boolean {
        if (!enabled) return false

        val type = options.type
        if (!allowed(type)) return true

        val keepPercent = when {
            type === ParticleTypes.EXPLOSION || type === ParticleTypes.EXPLOSION_EMITTER ||
                type === ParticleTypes.POOF -> explosions.value

            type === ParticleTypes.SMOKE || type === ParticleTypes.LARGE_SMOKE ||
                type === ParticleTypes.WHITE_SMOKE || type === ParticleTypes.CAMPFIRE_COSY_SMOKE ||
                type === ParticleTypes.CAMPFIRE_SIGNAL_SMOKE || type === ParticleTypes.DUST_PLUME -> smoke.value

            type === ParticleTypes.EFFECT || type === ParticleTypes.INSTANT_EFFECT ||
                type === ParticleTypes.ENTITY_EFFECT -> potionEffects.value

            options is BlockParticleOption -> blockDust.value

            else -> everythingElse.value
        }
        return roll(keepPercent)
    }

    /** Block-break crumbs / hit cracks (bypass createParticle). True = drop the whole effect. */
    @JvmStatic
    fun shouldDropBlockEffect(): Boolean {
        if (!enabled) return false
        if (particles.value["block"] == false) return true
        return roll(blockDust.value)
    }
}
