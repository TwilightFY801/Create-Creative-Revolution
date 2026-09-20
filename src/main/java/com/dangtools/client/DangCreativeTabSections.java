package com.dangtools.client;

import com.dangtools.Registration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 创造模式页签的「横幅分区」。
 *
 * <h2>旧版为什么会重叠（机主截图里的问题）</h2>
 * 旧版内容布局本身没错（每个分区先占一整行空白当横幅行），
 * 但渲染器用的是 {@code SECTION_ROWS} 里的<b>绝对行号</b>直接算 Y：
 * <pre>
 *   y = gridTop + row * ROW_HEIGHT      // 旧版：完全无视滚动量
 * </pre>
 * 而原版物品栏一屏只显示 5 行（{@code NUM_ROWS = 5}），滚动之后「第 0 行」根本不在屏幕上。
 * 于是旧版照样往那个坐标画 —— 两个分区的横幅被画到同一处、叠在一起，标题文字重影并压到物品格上。
 *
 * <h2>现在的做法（与 AeroEngine / fluidlogistics 一致）</h2>
 * <ol>
 *   <li>构建内容时把每个分区的<b>横幅行号</b>记进 {@link #SECTION_ROWS}；</li>
 *   <li>渲染时取当前滚动顶行 {@code currentRow}，算出<b>相对行</b>
 *       {@code rel = bannerRow - currentRow}；</li>
 *   <li>{@code rel < 0 || rel > 4} 的分区<b>直接不画</b>（不在可视 5 行里）；</li>
 *   <li>Y 用 {@code gridTop + rel * ROW_HEIGHT} —— 横幅始终贴在自己那一行上，随滚动一起走。</li>
 * </ol>
 * 本类只用通用类（不含客户端专有类），因为 {@code CreativeModeTabMixin} 在双端都会加载。
 */
public final class DangCreativeTabSections {

    /** 原版创造物品栏每行 9 格。 */
    public static final int ROW_SIZE = 9;

    /** 原版创造物品栏一屏可见 5 行（{@code CreativeModeInventoryScreen.NUM_ROWS = 5}）。 */
    public static final int VISIBLE_ROWS = 5;

    /** 一行的高度（像素），与原版物品格间距一致。 */
    public static final int ROW_HEIGHT = 18;

    /** 一个分区：id（同时是横幅贴图名）与标题。 */
    public record Section(String id, Component title) {}

    public static final List<Section> SECTIONS = List.of(
            new Section("cpc", Component.translatable("itemGroup.dangtools.cpc")),
            new Section("revolution", Component.translatable("itemGroup.dangtools"))
    );

    /** 分区 id -> 横幅所在行号（页签内容里的绝对行号）。 */
    public static final Map<String, Integer> SECTION_ROWS = new LinkedHashMap<>();

    private DangCreativeTabSections() {}

    /**
     * 构建页签内容：逐分区输出「横幅行（9 格空白）+ 该分区物品」。
     * <p>
     * 横幅行整行留空，物品不占横幅行，所以横幅永远压在空白上、不会盖住任何物品。
     */
    public static void buildContents(Consumer<ItemStack> itemOutput, Consumer<ItemStack> searchOutput) {
        SECTION_ROWS.clear();
        int row = 0;
        for (Section section : SECTIONS) {
            // 横幅行：整行 9 格空白
            SECTION_ROWS.put(section.id(), row);
            for (int i = 0; i < ROW_SIZE; i++) {
                itemOutput.accept(ItemStack.EMPTY);
            }
            row++;

            // 该分区的物品
            List<ItemStack> items = itemsFor(section.id());
            for (ItemStack stack : items) {
                itemOutput.accept(stack);
                searchOutput.accept(stack);
            }
            int used = items.size() % ROW_SIZE;
            if (used != 0) {
                for (int i = used; i < ROW_SIZE; i++) {
                    itemOutput.accept(ItemStack.EMPTY);
                }
                row++;
            }
            row += items.size() / ROW_SIZE;
        }
    }

    private static List<ItemStack> itemsFor(String id) {
        List<ItemStack> list = new ArrayList<>();
        if ("cpc".equals(id)) {
            list.add(new ItemStack(Registration.HAMMER.get()));
            list.add(new ItemStack(Registration.SICKLE.get()));
            list.add(new ItemStack(Registration.PARTY_TOOL.get()));
            list.add(new ItemStack(Registration.FLAG_ITEM.get()));
            list.add(new ItemStack(Registration.FLIGHT_LICENSE.get()));
        } else {
            list.add(new ItemStack(Registration.PARTY_POWER_ITEM.get()));
            list.add(new ItemStack(Registration.PARTY_WEIGHT_ITEM.get()));
            list.add(new ItemStack(Registration.DUAL_WHEEL_SUSPENSION_ITEM.get()));
            list.add(new ItemStack(Registration.TRIM_BALLAST_ITEM.get()));
            list.add(new ItemStack(Registration.MASS_SCANNER.get()));
            list.add(new ItemStack(Registration.GRIP_TIRE.get()));
            list.add(new ItemStack(Registration.SMALL_GRIP_TIRE.get()));
            list.add(new ItemStack(Registration.LARGE_GRIP_TIRE.get()));
            list.add(new ItemStack(Registration.MONSTROUS_GRIP_TIRE.get()));
            list.add(new ItemStack(Registration.DRIFT_TIRE.get()));
            list.add(new ItemStack(Registration.SMALL_DRIFT_TIRE.get()));
            list.add(new ItemStack(Registration.LARGE_DRIFT_TIRE.get()));
            list.add(new ItemStack(Registration.MONSTROUS_DRIFT_TIRE.get()));

            // ---- 4/4 灯光系统 ----
            list.add(new ItemStack(Registration.LIGHTING_LEVER_ITEM.get()));
            list.add(new ItemStack(Registration.HEADLIGHT_ITEM.get()));
            list.add(new ItemStack(Registration.BRAKE_LIGHT_ITEM.get()));
            list.add(new ItemStack(Registration.LEFT_TURN_SIGNAL_ITEM.get()));
            list.add(new ItemStack(Registration.RIGHT_TURN_SIGNAL_ITEM.get()));
            list.add(new ItemStack(Registration.INTERIOR_LIGHT_ITEM.get()));
        }
        return list;
    }

    /** 分区的横幅贴图：assets/textures/gui/sprites/creative_tab/&lt;id&gt;.png */
    public static ResourceLocation spriteFor(Section section) {
        return ResourceLocation.fromNamespaceAndPath("dangtools", "creative_tab/" + section.id());
    }
}
