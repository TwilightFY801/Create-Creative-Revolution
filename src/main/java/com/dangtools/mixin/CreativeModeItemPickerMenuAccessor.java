package com.dangtools.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把 {@code CreativeModeInventoryScreen$ItemPickerMenu.getRowIndexForScroll(float)} 提升为可调用。
 * <p>
 * 原版方法是 {@code protected}，我们没法直接调。参考实现里
 * fluidlogistics 用了一个同名的 {@code CreativeModeItemPickerMenuAccessor}
 * （{@code @Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)} + 抽象方法），
 * AeroEngine 则直接在 {@code ItemPickerMenu} 上 mixin 了 {@code scrollTo}。
 * 这里采用 fluidlogistics 的写法 —— 更直接，也不依赖滚动事件是否被触发。
 */
@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface CreativeModeItemPickerMenuAccessor {

    /** 把滚动位置（0~1）换算成「当前顶行在第几行」。 */
    @Invoker("getRowIndexForScroll")
    int dangtools$getRowIndexForScroll(float scrollOffs);
}
