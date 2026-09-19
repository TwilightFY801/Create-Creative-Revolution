package com.dangtools.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取 AbstractContainerScreen 的 leftPos / topPos，用来定位背包网格。 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int dangtools$getLeftPos();

    @Accessor("topPos")
    int dangtools$getTopPos();
}
