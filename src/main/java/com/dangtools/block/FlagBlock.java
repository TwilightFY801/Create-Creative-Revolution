package com.dangtools.block;

import com.dangtools.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 国旗：可以放在任何地方（不再限制传动杆）。
 * 范围内玩家获得满级信标增益（64×64×64），每 4 秒刷新一次。
 */
public class FlagBlock extends Block implements EntityBlock {

    /** 薄薄的一张旗帜（与模型一致：一片薄板）。 */
    protected static final VoxelShape SHAPE = Block.box(0.5, 5.5, 7.75, 15.5, 15.5, 8.25);

    public FlagBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlagBlockEntity(Registration.FLAG_BE.get(), pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> FlagBlockEntity.tick(lvl, pos, st, (FlagBlockEntity) be);
    }
}
