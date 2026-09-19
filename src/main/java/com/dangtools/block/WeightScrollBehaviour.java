package com.dangtools.block;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 「重量」调节行为：面板长度约 300 格，但数值范围仍是 0 ~ 1000。
 * <p>
 * 原因：Create 的 {@link ScrollValueBehaviour#createBoard} 直接拿 max(=1000) 当格子数，
 * 1000 格会撑出屏幕。这里把面板压到 {@link #BOARD_STEPS} 格，
 * 并用<b>线性映射</b>把格子换算成 0~1000 的实际数值（每格约 3.33，不截断、不失真）。
 */
public class WeightScrollBehaviour extends ScrollValueBehaviour {

    /** 面板格数（0~300，共 301 格）——长度约为 1000 格的三分之一，能稳稳在屏幕内。 */
    public static final int BOARD_STEPS = 300;

    public WeightScrollBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slotPositioning) {
        super(label, be, slotPositioning);
    }

    /** 面板格子 -> 实际数值（线性映射到 0~max）。 */
    private int cellToValue(int cell) {
        return Math.round(cell * (float) max / BOARD_STEPS);
    }

    /** 实际数值 -> 面板格子。 */
    private int valueToCell(int value) {
        return max <= 0 ? 0 : Math.round(value * (float) BOARD_STEPS / max);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(
                label,
                BOARD_STEPS,
                30,
                ImmutableList.of(Component.literal("重量")),
                new ValueSettingsFormatter(
                        settings -> Component.literal(cellToValue(settings.value()) + " kpg")));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrl) {
        if (!settings.equals(getValueSettings())) {
            playFeedbackSound(this);
        }
        setValue(cellToValue(settings.value()));
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, valueToCell(value));
    }
}
