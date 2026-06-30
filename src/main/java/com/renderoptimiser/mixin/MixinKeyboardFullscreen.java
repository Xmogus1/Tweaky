package com.renderoptimiser.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.renderoptimiser.features.impl.gui.BorderlessWindowed;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// F11 fullscreen intercept for MC 26.1.2, where the keybind is handled INLINE inside
// KeyboardHandler#keyPress (there is no Minecraft#toggleFullscreen on this tree). We wrap the
// single Window#toggleFullScreen() call in keyPress: when Borderless Windowed is enabled we run
// our borderless toggle and swallow the original call so MC never flips its own fullscreen mode.
// require = 0: on 26.2 keyPress does not call Window#toggleFullScreen(), so this no-ops there —
// see MixinMinecraftFullscreen for the 26.2 path.
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardFullscreen {
    @WrapOperation(
        method = "keyPress",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;toggleFullScreen()V"),
        require = 0
    )
    private void tweaky$borderlessF11(Window window, Operation<Void> original) {
        if (BorderlessWindowed.INSTANCE.enabled) {
            BorderlessWindowed.INSTANCE.toggleBorderless();
            return;
        }
        original.call(window);
    }
}
