package com.renderoptimiser.features.impl.sound

import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.features.Feature
import com.renderoptimiser.mixin.IAbstractSoundInstance
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource

/**
 * Replaces player hurt sounds with the classic "oof" (bundled at
 * assets/tweaky/sounds/classic_hurt.ogg, registered via assets/tweaky/sounds.json).
 *
 * Interception lives in [com.renderoptimiser.mixin.MixinSoundManager]: when a hurt sound is about
 * to play, [replaceSound] hands back a positioned replacement instance (same spot, same pitch,
 * original volume x the Volume slider) and the vanilla one is cancelled. Covers every player —
 * you and others — since all player damage routes through the same sound events.
 */
object ClassicHurt: Feature("Replaces the player hurt sound with the classic \"oof\".") {

    private val volume by SliderSetting("Volume", 1f, 0f, 2f, 0.05f)
        .withDescription("Volume multiplier on top of the original hurt sound's volume.")

    /** Client-side only — resolved against our sounds.json by id, no registry needed. */
    private val CLASSIC: SoundEvent = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(MOD_ID, "classic_hurt")
    )

    private val random = RandomSource.create()

    private val hurtIds = setOf(
        SoundEvents.PLAYER_HURT.location,
        SoundEvents.PLAYER_HURT_DROWN.location,
        SoundEvents.PLAYER_HURT_FREEZE.location,
        SoundEvents.PLAYER_HURT_ON_FIRE.location,
        SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH.location,
    )

    /** Returns the replacement instance for a player-hurt sound, or null to leave it alone. */
    @JvmStatic
    fun replaceSound(sound: SoundInstance): SoundInstance? {
        if (! enabled) return null
        return runCatching {
            if (sound.identifier !in hurtIds) return null

            // NEVER call sound.getVolume()/getPitch() here: they dereference the RESOLVED Sound,
            // which is null before the engine resolves the instance — the resulting NPE inside the
            // sound-packet handler disconnects the player. Read the raw fields via accessor instead.
            val rawVolume = (sound as? IAbstractSoundInstance)?.tweaky_rawVolume() ?: 1f
            val rawPitch = (sound as? IAbstractSoundInstance)?.tweaky_rawPitch() ?: 1f

            SimpleSoundInstance(
                CLASSIC,
                sound.source,
                rawVolume * volume.value,
                rawPitch,
                random,
                sound.x, sound.y, sound.z,
            )
        }.getOrNull() // any failure -> keep the vanilla sound instead of risking a disconnect
    }
}
