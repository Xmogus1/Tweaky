package com.renderoptimiser.features.impl.misc.sound

import com.renderoptimiser.features.Feature
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvents


/**
 * @see com.renderoptimiser.mixin.MixinSoundManager
 */
object ArrowHitSound: Feature() {
    private val soundConfig = createSoundSettings("Sound", SoundEvents.NOTE_BLOCK_HARP.value())

    @JvmStatic
    fun onSoundPlay(soundInstance: SoundInstance): Boolean {
        if (! enabled) return false
        if (soundInstance.identifier != SoundEvents.ARROW_HIT_PLAYER.location) return false
        soundConfig.play.action()
        return true
    }
}