package com.renderoptimiser.interfaces;

import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface implemented by MixinAbstractContainerScreen — exposes the protected hoveredSlot to
 * features (e.g. SlotLock's hover-and-press-to-lock). Lives outside the mixin package on purpose:
 * AutoMixinDiscovery registers every class there as a mixin.
 */
public interface IContainerScreen {
    @Nullable
    Slot tweaky_getHoveredSlot();
}
