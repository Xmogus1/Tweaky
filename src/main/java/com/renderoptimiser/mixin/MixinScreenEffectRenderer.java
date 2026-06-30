package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.Camera;
import com.renderoptimiser.features.impl.visual.LowFire;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {

    /**
     * LowFire — 26.2 VERSION (DIVERGENT: in 26.1.2 the entry/helper are named
     * renderScreenEffect/renderFire and the helper takes a MultiBufferSource). Wraps the fire call with a
     * push/translate/pop so the overlay renders lower; the pose is snapshotted at submit time, so
     * pre-translating is safe.
     */
    @WrapOperation(
            method = "submit(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"),
            require = 0
    )
    private static void tweaky$lowFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, Operation<Void> original) {
        float offset = LowFire.offset();
        if (offset <= 0f) {
            original.call(poseStack, collector, sprite);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0f, -offset, 0f);
        original.call(poseStack, collector, sprite);
        poseStack.popPose();
    }
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (Camera.INSTANCE.enabled && Camera.getHideFireOverlay().getValue()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onRenderWater(Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (Camera.INSTANCE.enabled && Camera.getHideWaterOverlay().getValue()) ci.cancel();
    }

    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void onRenderWafter(Player player, CallbackInfoReturnable<BlockState> cir) {
        if (Camera.INSTANCE.enabled && Camera.getHideBlockOverlay().getValue()) {
            cir.setReturnValue(null);
        }
    }
}