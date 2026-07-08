package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.SlotLock;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the in-game Q-drop (no screen open) when the SELECTED hotbar slot is locked by
 * {@link SlotLock}. Container-screen drops are already covered by the slotClicked funnel; this is the
 * held-item path (LocalPlayer.drop, identical in 26.1.2 and 26.2).
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerDrop {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$blockLockedDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (SlotLock.shouldBlockDrop()) cir.setReturnValue(false);
        } catch (Throwable ignored) {
        }
    }
}
