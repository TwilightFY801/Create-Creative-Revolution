package com.dangtools.client;

import com.dangtools.Registration;
import com.dangtools.mixin.AbstractContainerScreenAccessor;
import com.dangtools.mixin.CreativeModeInventoryScreenAccessor;
import com.dangtools.mixin.CreativeModeItemPickerMenuAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 在创造模式物品栏里绘制分区横幅（贴图 + 标题），观感对齐 AeroEngine / fluidlogistics。
 *
 * <h2>三个必须做对的地方</h2>
 * <ol>
 *   <li><b>必须随滚动位移。</b>取 {@code CreativeModeInventoryScreen.scrollOffs} 换算成当前顶行
 *       {@code currentRow}，横幅画在 {@code rel = bannerRow - currentRow} 行上。</li>
 *   <li><b>不在可视范围内的行不画。</b>原版一屏 5 行（{@code NUM_ROWS}），
 *       {@code rel < 0 || rel > 4} 直接跳过。</li>
 *   <li><b>必须画在物品格之后。</b>由 {@code ContainerScreenEvent.Render.Foreground} 调用。</li>
 * </ol>
 *
 * <h2>与两个参考实现的逐条对照（反编译核对）</h2>
 * <table border="1">
 *   <tr><th>项目</th><th>AeroEngine</th><th>fluidlogistics</th><th>本模组</th></tr>
 *   <tr><td>贴图</td>
 *       <td>{@code blitSprite(sprite,x,y,162,18)}；雪碧图 162×144 = 8 帧，
 *           mcmeta {@code frametime=3,width=162,height=18}</td>
 *       <td>{@code blit(sprite,8,y,162,18, u=f*18, v=0, 162,18, 162, frameCount*18)}；
 *           按"累计悬停毫秒"自己选帧（{@code HoverAnimation.sample}），贴图 162×180 = 10 帧</td>
 *       <td>{@code blitSprite(sprite,x,y,162,18)} + <b>与原版同款 mcmeta</b>
 *           （{@code frametime=3}），sprite 系统自动逐帧播放（与 AeroEngine 相同）；
 *           <b>悬停时额外叠一层暖色高光</b>（见 {@link #HOVER_TINT}）</td></tr>
 *   <tr><td>横幅位置</td>
 *       <td>{@code leftPos+8, topPos+17 + rel*18}</td>
 *       <td>{@code leftPos+8, topPos+17 + rel*18}（{@code BANNER_X_OFFSET=8, BANNER_Y_OFFSET=17}）</td>
 *       <td>同：{@code leftPos+8, topPos+17 + rel*18}</td></tr>
 *   <tr><td>可视区裁剪</td>
 *       <td>{@code if (rel<0||rel>4) continue;}</td>
 *       <td>{@code if (rel<0||rel>=5) continue;}（{@code VISIBLE_ROW_COUNT=5}）</td>
 *       <td>同：{@code rel<0||rel>=VISIBLE_ROWS(5)} 跳过</td></tr>
 *   <tr><td>滚动量怎么取</td>
 *       <td>mixin {@code ItemPickerMenu.scrollTo} 写 {@code currentRow}</td>
 *       <td>每帧 {@code menu.getRowIndexForScroll(screen.scrollOffs)}</td>
 *       <td>同 fluidlogistics（每帧现算，首次打开也可靠）</td></tr>
 *   <tr><td>渲染阶段</td>
 *       <td>{@code render} 的 TAIL</td>
 *       <td>{@code ContainerScreenEvent.Render.Foreground}</td>
 *       <td>同 fluidlogistics：{@code Foreground}</td></tr>
 *   <tr><td>标题底衬</td>
 *       <td>无（直接画字）</td>
 *       <td>{@code fill(10, rowY+2, maxX, rowY+16, 0xAA3A2A1C)}，横向铺满</td>
 *       <td>同 fluidlogistics：{@code 0xAA3A2A1C}，横向铺满横幅</td></tr>
 *   <tr><td>标题画法与渐变</td>
 *       <td>{@code drawString(..., false)}（无阴影）</td>
 *       <td>先 {@code drawString(..., BOTTOM, true)} 带阴影，
 *           再 {@code enableScissor(x, textY, x+w+1, textY+5)} 后
 *           {@code drawString(..., TOP, false)} 叠上半段亮色</td>
 *       <td><b>同 fluidlogistics</b>：阴影暗色打底 + scissor 只留上半 5px 叠亮色
 *           （见 {@link #drawTitle}）</td></tr>
 *   <tr><td>悬停动画</td>
 *       <td>无</td>
 *       <td>{@code HoverAnimation}：按"累计悬停毫秒 / 帧时长 % 帧数"选帧</td>
 *       <td>保留原版自动逐帧动画，<b>悬停时额外叠暖色高光</b>（见 {@link HoverState}）。
 *           说明：我们的贴图带 mcmeta、由 sprite 系统自动播放，插不进"手动选帧"，
 *           所以用叠加高光表达悬停反馈</td></tr>
 * </table>
 *
 * <h2>scissor 换算依据（重要）</h2>
 * {@code GuiGraphics.enableScissor(minX,minY,maxX,maxY)} 的四个参数是
 * <b>GUI 逻辑坐标</b>：它内部调 {@code applyScissor(...)}，由游戏按
 * {@code window.getGuiScale()} 自己换算（源码里就是
 * {@code double d0 = window.getGuiScale(); ... (int)(minX * d0)}）。
 * 所以<b>不需要我们自己乘 GUI scale</b> —— fluidlogistics 也是直接传 GUI 坐标
 * （它整个类里没有任何 {@code getGuiScale()} 调用），我们照它来。
 * 这也正是"照抄参考实现"比"自己推"更安全的地方。
 */
public final class DangCreativeTabBannerRenderer {

    private static final int BANNER_WIDTH = 162;
    private static final int BANNER_HEIGHT = 18;
    /** 底衬颜色：与 fluidlogistics 的 {@code TITLE_BACKGROUND = 0xAA3A2A1C} 同值。 */
    private static final int TEXT_BACKGROUND = 0xAA3A2A1C;
    /** 标题下半段颜色（暖金，对应 fluidlogistics 的 {@code TITLE_BOTTOM_COLOR} 色系）。 */
    private static final int TITLE_BOTTOM_COLOR = 0xFFCE9F1A;
    /** 标题上半段颜色（对应 fluidlogistics 的 {@code TITLE_TOP_COLOR = 0xFFFFEBDC}）。 */
    private static final int TITLE_TOP_COLOR = 0xFFFFEBDC;
    /** 渐变只覆盖标题上半部分的高度（fluidlogistics 用 {@code textY .. textY+5}）。 */
    private static final int TITLE_GRADIENT_HEIGHT = 5;
    /** 标题相对横幅左上角的偏移。 */
    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    /** 悬停高光颜色（暖金，半透明）。 */
    private static final int HOVER_TINT = 0x3CFFD98A;
    /** 物品网格相对 GUI 左上角的偏移（与原版创造栏一致）。 */
    private static final int GRID_LEFT_PADDING = 8;
    private static final int GRID_TOP_PADDING = 17;

    /** 每个分区的悬停状态（对应 fluidlogistics 的 {@code ANIMATIONS} map）。 */
    private static final java.util.Map<String, HoverState> HOVER_STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private DangCreativeTabBannerRenderer() {}

    /** 由 {@code ContainerScreenEvent.Render.Foreground} 调用（物品格画完之后）。 */
    public static void onRenderForeground(CreativeModeInventoryScreen screen, GuiGraphics graphics,
                                          int mouseX, int mouseY) {
        if (CreativeModeInventoryScreenAccessor.dangtools$getSelectedTab() != Registration.REVOLUTION_TAB.get()) {
            // 切走后清掉悬停状态，避免累积（fluidlogistics 也是这么做的）
            HOVER_STATES.clear();
            return;
        }
        render(screen, graphics, mouseX, mouseY);
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        // NOTE: ContainerScreenEvent.Render.Foreground fires INSIDE the pose that
        // AbstractContainerScreen.render already translated by (leftPos, topPos).
        // So we must use LOCAL coordinates relative to the panel's top-left corner here.
        // Adding leftPos/topPos again double-offsets the banner and draws it outside the panel.
        int x = GRID_LEFT_PADDING;
        int y = GRID_TOP_PADDING;
        Font font = Minecraft.getInstance().font;

        int currentRow = currentRow(screen);

        for (DangCreativeTabSections.Section section : DangCreativeTabSections.SECTIONS) {
            Integer bannerRow = DangCreativeTabSections.SECTION_ROWS.get(section.id());
            if (bannerRow == null) {
                continue;
            }
            // 相对行：横幅所在行相对于当前顶行
            int relativeRow = bannerRow - currentRow;
            // 不在可视的 5 行里 -> 不画
            if (relativeRow < 0 || relativeRow >= DangCreativeTabSections.VISIBLE_ROWS) {
                continue;
            }
            int bannerY = y + relativeRow * DangCreativeTabSections.ROW_HEIGHT;
            // mouseX/mouseY 是屏幕坐标，而 x / bannerY 是面板局部坐标，必须减去 leftPos/topPos 才能比较
            int localMouseX = mouseX - accessor.dangtools$getLeftPos();
            int localMouseY = mouseY - accessor.dangtools$getTopPos();
            boolean hovered = localMouseX >= x && localMouseX < x + BANNER_WIDTH
                    && localMouseY >= bannerY && localMouseY < bannerY + BANNER_HEIGHT;
            boolean hoverGlow = hoverState(section.id()).sample(hovered);
            drawBanner(graphics, font, section, x, bannerY, hoverGlow);
        }
    }

    /**
     * 当前顶行 = {@code menu.getRowIndexForScroll(scrollOffs)}。
     * <p>
     * 拿不到时退回 0（不可滚动的情况下本来就是 0，所以这个兜底是安全的）。
     */
    private static int currentRow(CreativeModeInventoryScreen screen) {
        try {
            float scrollOffs = ((CreativeModeInventoryScreenAccessor) screen).dangtools$getScrollOffs();
            CreativeModeItemPickerMenuAccessor menu =
                    (CreativeModeItemPickerMenuAccessor) (Object) screen.getMenu();
            return Math.max(menu.dangtools$getRowIndexForScroll(scrollOffs), 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 画一条横幅 + 标题。{@code hoverGlow} 为真时额外叠一层暖色高光（悬停反馈）。 */
    private static void drawBanner(GuiGraphics graphics, Font font,
                                   DangCreativeTabSections.Section section, int x, int y, boolean hoverGlow) {
        ResourceLocation sprite = DangCreativeTabSections.spriteFor(section);
        try {
            graphics.blitSprite(sprite, x, y, BANNER_WIDTH, BANNER_HEIGHT);
        } catch (Throwable t) {
            // 贴图缺失也不能把界面画崩：退化成纯色横幅
            graphics.fill(x, y, x + BANNER_WIDTH, y + BANNER_HEIGHT, 0xE0201A12);
            graphics.fill(x, y, x + BANNER_WIDTH, y + 1, 0xFFB08A3E);
            graphics.fill(x, y + BANNER_HEIGHT - 1, x + BANNER_WIDTH, y + BANNER_HEIGHT, 0xFFB08A3E);
        }
        // 悬停高光（悬停动画的视觉表现）
        if (hoverGlow) {
            graphics.fill(x, y, x + BANNER_WIDTH, y + BANNER_HEIGHT, HOVER_TINT);
        }
        drawTitle(graphics, font, section.title(), x, y);
    }

    /**
     * 标题：阴影暗色打底 + {@code scissor} 只保留上半段叠亮色 —— 即 fluidlogistics 的渐变做法。
     * <p>
     * {@code enableScissor} 的参数是 GUI 逻辑坐标（GUI scale 换算由游戏自己做，见类注释），
     * 所以这里直接传 {@code (textX, textY, textX+w+1, textY+5)}，与 fluidlogistics 一字不差。
     */
    private static void drawTitle(GuiGraphics graphics, Font font, Component title, int x, int y) {
        int textX = x + TITLE_X;
        int textY = y + TITLE_Y;
        int width = font.width(title);

        // 底衬：只包住【文字那一块】，不要铺满整条横幅。
        // （机主："以前是只有文字的那一块才会有，后面的就没有了" —— 所以不要 fluidlogistics 那种整条铺满。）
        graphics.fill(textX - 1, y + 2, textX + width + 1, y + BANNER_HEIGHT - 2, TEXT_BACKGROUND);

        // 整体先用"下半段"颜色 + 阴影打底
        graphics.drawString(font, title, textX, textY, TITLE_BOTTOM_COLOR, true);

        // 再把上半段 scissor 出来叠亮色，形成上亮下暗的渐变
        int scissorX2 = Math.min(textX + width + 1, x + BANNER_WIDTH - 2);
        boolean scissorPushed = false;
        try {
            graphics.enableScissor(textX, textY, scissorX2, textY + TITLE_GRADIENT_HEIGHT);
            scissorPushed = true;
            graphics.drawString(font, title, textX, textY, TITLE_TOP_COLOR, false);
        } catch (Throwable ignored) {
            // scissor 出问题就只保留下半段配色，界面不会花
        } finally {
            if (scissorPushed) {
                try {
                    graphics.disableScissor();
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
    }

    private static HoverState hoverState(String id) {
        return HOVER_STATES.computeIfAbsent(id, k -> new HoverState());
    }

    /**
     * 悬停状态：只在「鼠标停在该横幅上并持续约 120ms」时为真，
     * 避免鼠标快速扫过时闪一下。
     * <p>
     * 对应 fluidlogistics 的 {@code FluidLogisticsCreativeBanner$HoverAnimation} ——
     * 它同样记录「累计悬停毫秒」（{@code activeHoverMillis}）来决定动画帧；
     * 我们的贴图由 sprite 系统的 mcmeta 自动播放，插不进手动选帧，
     * 所以用同一个累计悬停时间来驱动高光，语义与它一致。
     */
    private static final class HoverState {
        private long activeMillis;
        private long lastSample = -1L;
        private boolean hoveredLast;

        boolean sample(boolean hovered) {
            long now = net.minecraft.Util.getMillis();
            if (lastSample >= 0L && now >= lastSample && hoveredLast && hovered) {
                activeMillis += now - lastSample;
            }
            lastSample = now;
            hoveredLast = hovered;
            if (!hovered) {
                // 移开就复位（下次悬停从头开始）
                activeMillis = 0L;
                return false;
            }
            // 悬停约 120ms 后才亮起
            return activeMillis >= 120L;
        }
    }
}
