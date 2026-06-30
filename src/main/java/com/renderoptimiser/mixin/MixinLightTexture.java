package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.misc.Camera;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Lightmap.class)
public abstract class MixinLightTexture {
    @Unique private final LightmapRenderState fullBrightRenderState = new LightmapRenderState();
    @Unique private boolean updated = false;

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private LightmapRenderState renderoptimiser$fullbright(LightmapRenderState renderState) {
        if (!Camera.INSTANCE.enabled || !Camera.getFullBright().getValue()) return renderState;
        if (updated) return fullBrightRenderState;

        // Lightmap#render early-returns when the render state's needsUpdate flag is false,
        // so the GPU light buffer is never written. The default LightmapRenderState()
        // constructor leaves needsUpdate = false, which is why full bright never actually
        // took effect. Force it true so our state is uploaded every frame it's active.
        fullBrightRenderState.needsUpdate = true;
        fullBrightRenderState.skyFactor = 1f;
        fullBrightRenderState.blockFactor = 0f;
        fullBrightRenderState.nightVisionEffectIntensity = 0f;
        fullBrightRenderState.darknessEffectScale = 0f;
        fullBrightRenderState.bossOverlayWorldDarkening = 0f;
        fullBrightRenderState.brightness = 1f;
        fullBrightRenderState.blockLightTint = new Vector3f(0f, 0f, 0f);
        fullBrightRenderState.skyLightColor = new Vector3f(1f, 1f, 1f);
        fullBrightRenderState.ambientColor = new Vector3f(1f, 1f, 1f);
        fullBrightRenderState.nightVisionColor = new Vector3f(1f, 1f, 1f);

        updated = true;
        return fullBrightRenderState;
    }
}