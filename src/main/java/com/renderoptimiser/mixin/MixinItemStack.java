package com.renderoptimiser.mixin;

import com.renderoptimiser.utils.location.LocationUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.renderoptimiser.RenderOptimiser.mc;

@Mixin(ItemStack.class)
public class MixinItemStack {
    @Inject(method = "applyAfterUseComponentSideEffects", at = @At("HEAD"), cancellable = true)
    private void onApplyCooldown(LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (user == mc.player && LocationUtils.inSkyblock) {
            if (stack.is(Items.ENDER_PEARL)) {
                cir.setReturnValue(stack);
            }
        }
    }
}
