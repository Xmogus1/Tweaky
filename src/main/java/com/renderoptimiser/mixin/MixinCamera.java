package com.renderoptimiser.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.renderoptimiser.features.impl.visual.Camera.*;

/**
 * NOTE: deliberately NO @Redirect anywhere in this class. NoammAddons (Tweaky's upstream) ships
 * the same camera hooks as @Redirects, and two mods redirecting one instruction is a guaranteed
 * boot crash ("Scanned 0 target(s)") when both are installed. @WrapOperation chains with other
 * mods' redirects, so Tweaky + NoammAddons can coexist.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow private float eyeHeightOld;
    @Shadow private float eyeHeight;

    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void wrapSetPosition(Camera instance, double x, double y, double z, Operation<Void> original, @Local(argsOnly = true) float partialTicks) {
        if (INSTANCE.enabled && getLegacySneakHeight().getValue()) {
            float standingHeight = 1.62f;
            float sneakingHeight = 1.27f;

            // 1.54f = Pure 1.8.9
            // 1.27f = Default Modern
            float targetHeight = 1.5f;

            float maxOffset = targetHeight - sneakingHeight;
            float totalCrouchDistance = standingHeight - sneakingHeight;

            float currentEyeHeight = Mth.lerp(partialTicks, eyeHeightOld, eyeHeight);

            if (currentEyeHeight < standingHeight) {
                double crouchAmount = (standingHeight - currentEyeHeight) / totalCrouchDistance;
                crouchAmount = Math.clamp(crouchAmount, 0, 1);
                double animatedOffset = crouchAmount * maxOffset;

                original.call(instance, x, y + animatedOffset, z);
                return;
            }
        }

        original.call(instance, x, y, z);
    }

    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double wrapCameraDistance(LivingEntity instance, Holder<Attribute> attribute, Operation<Double> original) {
        if (INSTANCE.enabled && getCustomCameraDistance().getValue()) {
            return getCameraDistance().getValue().doubleValue();
        }

        return original.call(instance, attribute);
    }

    //#if CHEAT
    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void overrideCameraPos(Camera instance, double x, double y, double z, Operation<Void> original) {
        com.renderoptimiser.features.impl.misc.NoRotate.cameraHook(instance, x, y, z, original);
    }
    //#endif

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        if (INSTANCE.enabled && getNoCameraClip().getValue()) {
            cir.setReturnValue(cameraDist);
        }
    }

    @Inject(method = "calculateFov", at = @At(value = "RETURN"), cancellable = true)
    private void calculateFovHook(float partialTicks, CallbackInfoReturnable<Float> cir) {
        // Base FOV = the Camera feature's custom FOV if enabled, else vanilla; then the Zoom feature
        // scales it. Done in ONE hook so ordering is deterministic (zoom always composes on top of a
        // custom FOV) rather than relying on inject order between two separate Camera mixins.
        float base = INSTANCE.enabled && getCustomFOV().getValue() ? getCustomFOVSlider().getValue().floatValue() : cir.getReturnValue();
        float zoom = com.renderoptimiser.features.impl.visual.Zoom.fovMultiplier();
        cir.setReturnValue(zoom != 1.0f ? base * zoom : base);
    }

    @ModifyExpressionValue(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float getMaxFov(float original) {
        return INSTANCE.enabled && getCustomFOV().getValue() ? getCustomFOVSlider().getValue().floatValue() : original;
    }
}