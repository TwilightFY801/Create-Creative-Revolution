package com.dangtools.client;

import com.dangtools.DangTools;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * 创造模式物品栏的分区横幅渲染挂载点。
 * <p>
 * <b>为什么用 {@link ContainerScreenEvent.Render.Foreground} 而不是 mixin 进 renderBg</b>：
 * 原版 {@code AbstractContainerScreen.render} 的顺序是
 * 「{@code renderBg}（背景 + 物品格花纹） → renderables → renderSlot（物品图标） → renderLabels」，
 * 之后再 post {@code Foreground} 事件。
 * 我们旧版挂在 {@code renderBg} 尾巴上，属于<b>背景阶段</b>，
 * 于是横幅被物品格和物品图标盖住 —— 现在改到 Foreground，横幅画在最上层。
 * 参考实现（fluidlogistics 的 {@code FluidLogisticsCreativeBanner}）也是订阅这个事件。
 */
@EventBusSubscriber(modid = DangTools.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DangCreativeTabBannerEvents {

    private DangCreativeTabBannerEvents() {}

    @SubscribeEvent
    public static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        if (event.getContainerScreen() instanceof CreativeModeInventoryScreen screen) {
            DangCreativeTabBannerRenderer.onRenderForeground(
                    screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }
}
