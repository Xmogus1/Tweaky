package com.renderoptimiser.features.impl.sound

import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.features.Feature
import com.renderoptimiser.mixin.IAbstractSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource

/**
 * Replaces every block-break sound with Minecraft 26.4's ice placement sound (bundled at
 * assets/tweaky/sounds/ice_place/, registered via assets/tweaky/sounds.json). The six place
 * variants are picked at random per break, exactly like vanilla rotates ice-place sounds.
 *
 * Interception lives in [com.renderoptimiser.mixin.MixinSoundManager]: when a sound whose id is a
 * `minecraft:block.<group>.break` is about to play, [replaceSound] hands back a positioned ice-place
 * instance at the same spot, volume and pitch, and the vanilla break sound is cancelled.
 */
object SatisfyingBreaks: Feature() {

    /** Client-side only — resolved against our sounds.json by id, no registry needed. */
    private val ICE: SoundEvent = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(MOD_ID, "ice_place")
    )

    private val random = RandomSource.create()

    /** Returns the ice-place replacement for a block-break sound, or null to leave it alone. */
    @JvmStatic
    fun replaceSound(sound: SoundInstance): SoundInstance? {
        if (! enabled) return null
        return runCatching {
            val id = sound.identifier
            // every block break fires minecraft:block.<group>.break
            if (id.namespace != "minecraft" || ! id.path.startsWith("block.") || ! id.path.endsWith(".break")) {
                return null
            }

            // NEVER call sound.getVolume()/getPitch() here: they dereference the RESOLVED Sound,
            // which is null before the engine resolves the instance — the resulting NPE inside the
            // sound-packet handler disconnects the player. Read the raw fields via accessor instead.
            val rawVolume = (sound as? IAbstractSoundInstance)?.tweaky_rawVolume() ?: 1f
            val rawPitch = (sound as? IAbstractSoundInstance)?.tweaky_rawPitch() ?: 1f

            SimpleSoundInstance(
                ICE,
                sound.source,
                rawVolume,
                rawPitch,
                random,
                sound.x, sound.y, sound.z,
            )
        }.getOrNull() // any failure -> keep the vanilla sound instead of risking a disconnect
    }
}
