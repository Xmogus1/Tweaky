package com.renderoptimiser.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.renderoptimiser.features.impl.visual.DynamicLights;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain half of {@link DynamicLights} — 26.2 VERSION (DIVERGENT FILE).
 *
 * The 4-arg static getLightCoords is the single funnel for terrain meshing (incl. the AO path via
 * BlockModelLighter$Cache), fluids, particles and block entities. In 26.2 it lives on
 * net.minecraft.util.LightCoordsUtil; in 26.1.2 it was on LevelRenderer — the 26.1.2 tree's copy of
 * this file targets that class instead (only the @Mixin target + method descriptor differ).
 *
 * THREADING: runs on SectionRenderDispatcher worker threads — DynamicLights.getLightLevel reads a
 * volatile immutable snapshot, no shared mutable state. @Local(argsOnly) avoids referencing the
 * version-specific BrightnessGetter type in the handler signature.
 */
@Mixin(LightCoordsUtil.class)
public abstract class MixinDynamicLightTerrain {

    @Inject(
            method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 0
    )
    private static void tweaky$dynamicLight(CallbackInfoReturnable<Integer> cir, @Local(argsOnly = true) BlockPos pos) {
        try {
            if (!DynamicLights.INSTANCE.enabled) return;
            int dyn = DynamicLights.getLightLevel(pos);
            if (dyn <= 0) return;
            int packed = cir.getReturnValueI();
            if (LightCoordsUtil.block(packed) < dyn) {
                cir.setReturnValue(LightCoordsUtil.withBlock(packed, dyn));
            }
        } catch (Throwable ignored) {
        }
    }
}
