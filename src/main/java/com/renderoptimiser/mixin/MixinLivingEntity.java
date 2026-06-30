package com.renderoptimiser.mixin;

import com.renderoptimiser.RenderOptimiser;
import com.renderoptimiser.features.impl.visual.Animations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {
    public MixinLivingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void overrideSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if (!Animations.INSTANCE.enabled) return;
        if (!this.is(RenderOptimiser.mc.player)) return;
        if (RenderOptimiser.mc.player.getMainHandItem().isEmpty()) return;

        if (!Animations.INSTANCE.getIgnoreHaste().getValue()) return;
        cir.setReturnValue(Animations.INSTANCE.getSwingSpeed().getValue());
    }
}
