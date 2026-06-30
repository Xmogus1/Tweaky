package com.renderoptimiser.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Raw volume/pitch fields of a sound instance. The public getVolume()/getPitch() multiply by the
 * RESOLVED Sound (null until the engine calls resolve()) — calling them from a SoundManager.play
 * HEAD hook NPEs inside the packet handler, which disconnects the player ("Internal Exception").
 */
@Mixin(AbstractSoundInstance.class)
public interface IAbstractSoundInstance {
    @Accessor("volume")
    float tweaky_rawVolume();

    @Accessor("pitch")
    float tweaky_rawPitch();
}
