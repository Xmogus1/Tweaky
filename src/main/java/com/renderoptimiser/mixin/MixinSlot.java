package com.renderoptimiser.mixin;

import com.renderoptimiser.interfaces.ICoordRememberingSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Slot.class)
public class MixinSlot implements ICoordRememberingSlot {
    @Shadow public int x;
    @Shadow public int y;

    @Unique public int originalX;
    @Unique public int originalY;

    @Override
    public void renderoptimiser_rememberCoords() {
        this.originalX = this.x;
        this.originalY = this.y;
    }

    @Override
    public void renderoptimiser_restoreCoords() {
        this.x = this.originalX;
        this.y = this.originalY;
    }

    @Override
    public void renderoptimiser_setX(int x) {
        this.x = x;
    }

    @Override
    public void renderoptimiser_setY(int y) {
        this.y = y;
    }
}