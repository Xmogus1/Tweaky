package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.HideRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class MixinRecipeBookComponent {
    @Shadow
    protected abstract void setVisible(boolean visible);

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!HideRecipeBook.INSTANCE.enabled) return;
        if (HideRecipeBook.getCloseRecipeBook().getValue()) {
            this.setVisible(false);
        }
    }
}