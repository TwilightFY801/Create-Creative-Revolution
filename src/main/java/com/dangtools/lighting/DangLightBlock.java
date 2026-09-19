package com.dangtools.lighting;

import com.dangtools.block.LightPlatformBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组所有车灯的公共实现。
 *
 * <h2>外观：完整整格正方体 + 原版染色玻璃贴图</h2>
 * 按机主要求，所有灯都是<b>完完整整的一个正方体</b>：
 * <ul>
 *   <li>{@link #getShape} 返回 {@link Shapes#block()}（整格无空隙）；</li>
 *   <li>模型是 {@code cube_all}（六面同一张贴图），贴图直接用原版染色玻璃 ——
 *       放出来就是"一块完整的染色玻璃方块"：大灯 = 白色，左右转向灯 = 黄色，
 *       刹车灯 = 红色，车内灯 = 白色。</li>
 * </ul>
 * 仍是 {@code noOcclusion()}（玻璃要透光）且 {@code noCollission()}（纯发光装饰、可穿过）。
 *
 * <h2>亮度怎么来的</h2>
 * 亮度统一存在方块状态属性 {@link #LEVEL}（0~15）里，由
 * {@code BlockBehaviour.Properties.lightLevel(...)} 读出来 ——
 * <b>光照引擎查询亮度时是 O(1)，不需要方块实体参与</b>，
 * 改变亮度只需 {@code level.setBlock(... setValue(LEVEL, n))}，光照会自动重算。
 * 各灯具体怎么算出这个数值，见 {@link DangLightBlockEntity} 的 {@code tick}。
 */
public class DangLightBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** 亮度 0~15。0 = 灭。 */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    /** 灯具种类，决定 {@link DangLightBlockEntity} 用哪套逻辑算亮度。 */
    public enum Kind {
        /** 大灯：拉杆开就亮 15，并在物理结构前方生成光锥方块。白色玻璃。 */
        HEADLIGHT,
        /** 刹车灯：拉杆开 = 常亮 10（不明显）；检测到后退键 = 15。红色玻璃。 */
        BRAKE,
        /** 左转向灯：按住左键亮 15。黄色玻璃。 */
        LEFT_TURN,
        /** 右转向灯：按住右键亮 15。黄色玻璃。 */
        RIGHT_TURN,
        /** 车内灯：按任意方向键 → 缓慢变暗；松开 → 缓慢变亮（上限 14）。白色玻璃。 */
        INTERIOR
    }

    private final Kind kind;

    /**
     * 这个方块类的序列化 codec。
     * <p>
     * 1.21 起 {@code HorizontalDirectionalBlock} 的 {@code codec()} 是抽象方法，必须实现。
     * 我们的灯是「同一个方块类 + 不同 kind」，而 {@code simpleCodec} 只能重建一个实例。
     * 这里让 codec <b>延迟解析</b>到「第一个真正注册过的灯实例」——
     * 本模组的灯只会经由物品/注册表获得，这个 codec 仅用于 {@code /setblock} 一类的数据指令，
     * 所以「解析到本模组的某个灯」已经足够正确，不需要按 kind 逐一还原。
     */
    private static final com.mojang.serialization.MapCodec<DangLightBlock> CODEC =
            simpleCodec(properties -> firstRegistered());

    private static final java.util.concurrent.atomic.AtomicReference<DangLightBlock> FIRST_INSTANCE =
            new java.util.concurrent.atomic.AtomicReference<>();

    public DangLightBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LEVEL, 0));
        FIRST_INSTANCE.compareAndSet(null, this);
    }

    private static DangLightBlock firstRegistered() {
        DangLightBlock first = FIRST_INSTANCE.get();
        return first != null ? first
                : new DangLightBlock(BlockBehaviour.Properties.of(), Kind.HEADLIGHT);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LEVEL, 0);
    }

    /** 完整整格形状（机主要求：完完整整一个正方体）。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DangLightBlockEntity(com.dangtools.Registration.LIGHTING_LIGHT_BE.get(), pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // 灯的状态全部由服务端算（方块状态会同步到客户端），所以两端都挂同一个 ticker，
        // 由 DangLightBlockEntity 内部用 level.isClientSide 提前返回。
        return (lvl, pos, st, be) -> LightPlatformBlockEntity.tick(lvl, pos, st, (LightPlatformBlockEntity) be);
    }

    // ---------------- 静态工厂：每种灯一个，差异只在 kind ----------------

    /** 灯的亮度函数：读方块状态里的 {@link #LEVEL}，光照引擎查询时是 O(1)。 */
    public static java.util.function.ToIntFunction<BlockState> levelFunction() {
        return state -> state.hasProperty(LEVEL) ? state.getValue(LEVEL) : 0;
    }

    public static DangLightBlock headlight(Properties properties) {
        return new DangLightBlock(lightProperties(properties), Kind.HEADLIGHT);
    }

    public static DangLightBlock brakeLight(Properties properties) {
        return new DangLightBlock(lightProperties(properties), Kind.BRAKE);
    }

    public static DangLightBlock leftTurnSignal(Properties properties) {
        return new DangLightBlock(lightProperties(properties), Kind.LEFT_TURN);
    }

    public static DangLightBlock rightTurnSignal(Properties properties) {
        return new DangLightBlock(lightProperties(properties), Kind.RIGHT_TURN);
    }

    public static DangLightBlock interiorLight(Properties properties) {
        return new DangLightBlock(lightProperties(properties), Kind.INTERIOR);
    }

    /**
     * 灯具通用属性：
     * <ul>
     *   <li>{@code noOcclusion()}：玻璃类要透光，不能当实心遮挡；</li>
     *   <li>{@code noCollission()}：纯发光装饰，可以穿过去；</li>
     *   <li>{@code instabreak()}：玻璃手感，一挖就掉；</li>
     *   <li>{@code lightLevel(levelFunction())}：亮度按方块状态 {@link #LEVEL} 发光。</li>
     * </ul>
     */
    private static Properties lightProperties(Properties properties) {
        // 机主要求：真实的 1x1x1 碰撞体积（原来 noCollission() 会被人直接穿过去）
                return properties.noOcclusion().instabreak().lightLevel(levelFunction());
    }
}
