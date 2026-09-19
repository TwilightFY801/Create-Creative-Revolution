package com.dangtools.lighting;

import com.dangtools.block.LightingLeverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 照明拉杆：整套灯光系统的总开关。
 * <ul>
 *   <li><b>右键</b> = 拉动 / 关闭（切换 {@link #LIT}）；</li>
 *   <li><b>潜行右键</b> = 打开按键配置界面（客户端 Screen，见
 *       {@code com.dangtools.client.LightingLeverConfigScreen}），可改前进/后退/左/右四个 keybind。</li>
 * </ul>
 * 开启后：大灯亮 + 刹车灯常亮(10) + 氛围灯亮 + 车内灯按按键自动缓慢调节。
 * <p>
 * 拉杆本身不发光（是装饰/开关），灯亮不亮由各灯自己读拉杆状态决定 —— 见 {@link LeverRegistry}。
 * <p>
 * 关于「打开界面」：方块类双端都要加载，所以这里<b>只写 {@code if (level.isClientSide)}
 * 再调用客户端专用方法</b>。服务端执行时那个分支不会被解析到，不会去加载 {@code Minecraft} 类
 * （和本模组 {@code DangTools} 构造器里判断 {@code FMLEnvironment.dist} 是同一个思路）。
 */
public class LightingLeverBlock extends HorizontalDirectionalBlock {

    /** 总开关状态：true = 已拉动（灯可以亮）。 */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /** 序列化 codec（1.21 起 HorizontalDirectionalBlock 要求实现；说明见 DangLightBlock）。 */
    public static final com.mojang.serialization.MapCodec<LightingLeverBlock> CODEC =
            simpleCodec(properties -> new LightingLeverBlock(properties.noOcclusion()));

    public LightingLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** 原版拉杆的底座形状（`lever.json` 里底座 element 是 [5,0,4] → [11,3,12]），按朝向旋转。 */
    private static final net.minecraft.world.phys.shapes.VoxelShape BASE =
            Block.box(5.0D, 0.0D, 4.0D, 11.0D, 3.0D, 12.0D);
    private static final net.minecraft.world.phys.shapes.VoxelShape BASE_EAST =
            Block.box(4.0D, 0.0D, 5.0D, 12.0D, 3.0D, 11.0D);

    /**
     * 机主要求：<b>和原版拉杆一样</b>（原来返回整格 {@code Shapes.block()} 太大）。
     * 原版拉杆的碰撞箱只有底座那一小块，所以这里给底座形状，并按 FACING 旋转。
     */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST, WEST -> BASE_EAST;
            default -> BASE;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, Boolean.FALSE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            // 潜行右键 = 打开配置界面（只在客户端）
            if (level.isClientSide) {
                openConfigScreen();
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), 3);
            LightingLeverBlockEntity.broadcastChange(level, pos);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    lit ? "dangtools.lighting_lever.on" : "dangtools.lighting_lever.off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 只在客户端分支被调用。 */
    private static void openConfigScreen() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(new com.dangtools.client.LightingLeverConfigScreen(minecraft.screen));
            }
        } catch (Throwable t) {
            com.dangtools.DangTools.LOGGER.warn("[dangtools] 打开照明拉杆配置界面失败: {}", t.toString());
        }
    }

    /** 被拆掉/替换时清掉登记与已生成的大灯光方块，避免残留。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            LeverRegistry.unregister(serverLevel, pos);
            LightingLeverBlockEntity.releaseLights(serverLevel, pos);
        }
    }
}
