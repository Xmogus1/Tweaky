package com.renderoptimiser.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.renderoptimiser.features.impl.misc.GuiMove;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping {
    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean tweaky$guiMove(boolean original) {
        Boolean override = GuiMove.INSTANCE.overrideIsDown((KeyMapping) (Object) this);
        return override != null ? override : original;
    }
}
