package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the {@link Zoom} feature is actively zooming, the mouse wheel adjusts the zoom level instead of
 * the held hotbar slot. MouseHandler#onScroll(long, double, double) is the GLFW scroll callback; the third
 * arg is the vertical wheel delta. The signature is byte-identical in 26.1.2 and 26.2, so this file is
 * shared verbatim.
 *
 * Only cancels when Zoom actually consumes the scroll (feature enabled + key held in-world), so ordinary
 * hotbar scrolling and GUI scrolling are never affected. Fail-safe: require = 0 + try/catch.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerZoom {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        try {
            if (Zoom.onScroll(vertical)) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
