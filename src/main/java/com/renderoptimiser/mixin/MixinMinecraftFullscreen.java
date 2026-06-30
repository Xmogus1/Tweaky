package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.BorderlessWindowed;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// F11 fullscreen intercept for MC 26.2, where the keybind is handled by the private
// Minecraft#toggleFullscreen(). When Borderless Windowed is enabled we toggle the borderless
// window state instead of MC's real fullscreen, then cancel so MC never flips its own mode.
// require = 0: on 26.1.2 this method does not exist (the F11 body is inlined in
// KeyboardHandler#keyPress), so the injector simply no-ops there — see MixinKeyboardFullscreen.
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFullscreen {
    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$borderlessF11(CallbackInfo ci) {
        if (BorderlessWindowed.INSTANCE.enabled) {
            BorderlessWindowed.INSTANCE.toggleBorderless();
            ci.cancel();
        }
    }
}
