package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.sound.ArrowHitSound;
import com.renderoptimiser.features.impl.sound.ClassicHurt;
import com.renderoptimiser.features.impl.sound.SatisfyingBreaks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class MixinSoundManager {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (ArrowHitSound.onSoundPlay(sound)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }

        // ClassicHurt: swap player hurt sounds for the classic "oof". The nested play() re-enters
        // this hook with the replacement instance, which no feature matches, so no recursion.
        SoundInstance classic = ClassicHurt.replaceSound(sound);
        if (classic != null) {
            cir.setReturnValue(((SoundManager) (Object) this).play(classic));
            return;
        }

        // SatisfyingBreaks: swap every block-break sound for the 26.4 ice placement sound. The
        // nested play() re-enters with the replacement, whose id is not a break sound, so no recursion.
        SoundInstance ice = SatisfyingBreaks.replaceSound(sound);
        if (ice != null) {
            cir.setReturnValue(((SoundManager) (Object) this).play(ice));
        }
    }
}