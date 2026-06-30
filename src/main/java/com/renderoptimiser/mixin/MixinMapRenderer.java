package com.renderoptimiser.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renderoptimiser.features.impl.visual.MapPlayerHeads;
import com.renderoptimiser.utils.render.MapDecorationSkin;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player heads on maps (see {@link MapPlayerHeads}).
 *
 * extractRenderState TAIL: match player-arrow decorations to nearby players and stash their skin on the
 * decoration state. render: the decoration sprite submit (ordinal 1 — ordinal 0 is the map background) is
 * wrapped; when a skin is stashed we draw the skin's face + hat layers instead of the arrow sprite,
 * counter-rotated so the head stays upright. Covers held maps AND item frames (same render path).
 * All signatures verified byte-identical in 26.1.2 and 26.2.
 */
@Mixin(MapRenderer.class)
public abstract class MixinMapRenderer {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void tweaky$assignSkins(MapId mapId, MapItemSavedData savedData, MapRenderState state, CallbackInfo ci) {
        try {
            MapPlayerHeads.assignSkins(savedData, state);
        } catch (Throwable ignored) {
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void tweaky$renderHeadDecoration(
            SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer, Operation<Void> original,
            @Local MapRenderState.MapDecorationRenderState decoration,
            @Local(argsOnly = true) int light
    ) {
        Identifier skin = null;
        try {
            if (MapPlayerHeads.INSTANCE.enabled && decoration != null) {
                skin = ((MapDecorationSkin) (Object) decoration).getTweaky_skin();
            }
        } catch (Throwable ignored) {
        }
        if (skin == null) {
            original.call(collector, poseStack, renderType, renderer);
            return;
        }

        poseStack.pushPose();
        // Undo the sprite half-pixel offset, then counter-rotate so the head stays upright.
        poseStack.translate(0.125f, -0.125f, 0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-(decoration.rot * 360f / 16f)));
        collector.submitCustomGeometry(poseStack, RenderTypes.text(skin), (pose, vc) -> {
            tweaky$headQuad(pose, vc, 0f, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, light);       // face
            tweaky$headQuad(pose, vc, -0.001f, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, light); // hat layer
        });
        poseStack.popPose();
    }

    @org.spongepowered.asm.mixin.Unique
    private static void tweaky$headQuad(PoseStack.Pose pose, VertexConsumer vc, float z,
                                        float u0, float v0, float u1, float v1, int light) {
        vc.addVertex(pose, -1f, 1f, z).setColor(-1).setUv(u0, v1).setLight(light);
        vc.addVertex(pose, 1f, 1f, z).setColor(-1).setUv(u1, v1).setLight(light);
        vc.addVertex(pose, 1f, -1f, z).setColor(-1).setUv(u1, v0).setLight(light);
        vc.addVertex(pose, -1f, -1f, z).setColor(-1).setUv(u0, v0).setLight(light);
    }
}
