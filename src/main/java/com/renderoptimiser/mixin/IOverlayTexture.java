package com.renderoptimiser.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes OverlayTexture's backing DynamicTexture so HurtFlash can repaint the red hurt band
 * (rows V=0..7 of the 16x16 overlay image) and re-upload it at runtime. Identical in 26.1.2 and 26.2.
 */
@Mixin(OverlayTexture.class)
public interface IOverlayTexture {
    @Accessor("texture")
    DynamicTexture getTexture();
}
