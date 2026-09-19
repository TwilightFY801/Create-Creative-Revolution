package com.dangtools.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取创造模式物品栏的<b>滚动状态</b>。
 * <p>
 * 这是「横幅随滚动正确位移」的关键：原版把滚动位置存在
 * {@code CreativeModeInventoryScreen.scrollOffs}（0~1 的 float），
 * 真正可见的是 {@code menu.getRowIndexForScroll(scrollOffs)} 指定的那 <b>5 行</b>
 * （{@code NUM_ROWS = 5}，{@code NUM_COLS = 9}）。
 * <p>
 * 参考实现（fluidlogistics 的 {@code FluidLogisticsCreativeBanner}）就是拿这个值来算
 * 「横幅相对第几行」，我们也走同一条路。行号换算在
 * {@link CreativeModeItemPickerMenuAccessor} 里。
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {

    @Accessor("selectedTab")
    static CreativeModeTab dangtools$getSelectedTab() {
        throw new AssertionError();
    }

    /** 当前滚动位置（0~1）。 */
    @Accessor("scrollOffs")
    float dangtools$getScrollOffs();
}
