package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.Cosmetics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmetics custom-size hooks. Both targets javap-verified byte-identical on 26.1.2 and 26.2:
 * extractRenderState stashes the GameProfile on the render state, scale applies the size.
 *
 * priority 500: LOWER than NoammAddons' default 1000 so our hooks run FIRST at a shared
 * injection point (see MixinFont for why lower priority = runs first). Our scale hook applies
 * Tweaky's size and (via NoammCompat) blanks the profile Noamm stashed on the render state,
 * so Noamm's later hook skips the player instead of stacking a second scale on top.
 */
@Mixin(value = AvatarRenderer.class, priority = 500)
public class MixinAvatarRenderer {
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
    private void tweaky$scale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        Cosmetics.scaleHook(state, poseStack);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    private void tweaky$extractRenderState(Avatar avatar, AvatarRenderState state, float f, CallbackInfo ci) {
        Cosmetics.extractRenderStateHook(avatar, state);
    }
}
