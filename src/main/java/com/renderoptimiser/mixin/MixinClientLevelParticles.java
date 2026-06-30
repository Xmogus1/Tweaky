package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.ParticleControl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block-break crumbs (addDestroyBlockEffect) and block-hit cracks (addBreakingBlockEffect) construct
 * their particles directly via ParticleEngine.add — they never pass createParticle, so
 * {@link ParticleControl} needs these dedicated hooks. Identical signatures in 26.1.2 and 26.2.
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelParticles {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$destroyEffect(BlockPos pos, BlockState state, CallbackInfo ci) {
        try {
            if (ParticleControl.shouldDropBlockEffect()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$breakingEffect(BlockPos pos, Direction direction, CallbackInfo ci) {
        try {
            if (ParticleControl.shouldDropBlockEffect()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
