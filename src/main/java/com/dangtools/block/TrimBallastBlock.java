package com.dangtools.block;

import com.dangtools.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 微调配重块：比重力方块更精细的配重，用于小型大运精确配平。
 * <p>
 * 与 {@link PartyWeightBlock}（0~1000 粗调）的区别在于<b>独立的方块状态属性</b>：
 * 这里 {@link #WEIGHT} 只有 <b>0~100</b> 共 101 档，<b>1 格 = 1 kpg</b>。
 * 两个方块各有自己的属性，互不影响。
 * <p>
 * 质量仍由数据包 data/dangtools/physics_block_properties/trim_ballast.json
 * （101 条：weight=N → sable:mass N.0）映射成 Sable 的真实质量。
 */
public class TrimBallastBlock extends Block implements EntityBlock {

    /** 重量档位 0~100（数值即质量，1 格 = 1 kpg）。 */
    public static final IntegerProperty WEIGHT = IntegerProperty.create("weight", 0, 100);

    public TrimBallastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WEIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WEIGHT, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrimBallastBlockEntity(Registration.TRIM_BALLAST_BE.get(), pos, state);
    }
}
