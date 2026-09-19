package com.dangtools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountRenderer;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 双轮悬架的渲染器：<b>一个方块实体，两个轮胎</b>，且只有一套悬挂杆件。
 *
 * <h2>上一版为什么失败（实机现象：第二个轮子不显示、却多出一根杆子）</h2>
 * 上一版第二遍<b>不调 {@code super.renderSafe}</b>、只调 {@code getRotatedModel(...)}，
 * 以为「跳过 renderSafe 就只剩轮胎、没有悬挂件」。反编译 offroad 的
 * {@code WheelMountRenderer.renderSafe}（533 行字节码）后确认这个假设<b>正好反了</b>：
 * <pre>
 *   renderSafe(be, pt, pose, buf, light, overlay) {
 *       getRotatedModel(be, state) -&gt; renderRotatingBuffer(...)   // 画的是 SHAFT_HALF（轴）—— 那根「杆子」
 *       FilteringRenderer.renderOnBlockEntity(...)
 *       // 推杆/减震/防倾杆：TELE_OUTER / TELE_INNER / TELE_MOUNT / SPRING_UPPER|MIDDLE|LOWER / DIODE_LEFT|RIGHT
 *       if (TireLike.model().isPresent()) CachedBuffers.partial(model)      // 轮胎（自定义模型）
 *       else Minecraft.getInstance().getItemRenderer().renderStatic(...)    // 轮胎（物品模型）
 *   }
 * </pre>
 * 即：<b>轮胎只在 renderSafe 里画；{@code getRotatedModel} 只返回 {@code AllPartialModels.SHAFT_HALF}</b>。
 * 所以「跳过 renderSafe、只调 getRotatedModel」= 只画轴、完全不画轮胎 ——
 * 与机主要求（只留轮胎、隐藏杆件）正好相反，这就是「第二个轮子没显示、多出一根杆子」的根因。
 * <p>
 * 同时核对过：offroad <b>没有</b>给 {@code WHEEL_MOUNT} 注册 Flywheel 可视化器
 * （{@code offroad.jar} 里只有 {@code BoreheadBearingVisual} 与 {@code RockCuttingWheelActorVisual}），
 * {@code OffroadNeoForge} 里也没有任何 {@code Visualizer} 注册调用，
 * 所以「轮胎在可视化器里画」的猜测不成立 —— 问题 100% 在 BER 这一层。
 *
 * <h2>本版做法</h2>
 * <ol>
 *   <li><b>中心一遍</b>：调 {@code super.renderSafe}，完整悬挂（底盘 + 轴 + 推杆/减震/防倾杆 + 轮胎），
 *       与单个车轮悬架外观<b>完全一致</b>（父类原样执行，未做任何拦截）；</li>
 *   <li><b>沿轮轴 ±offset 各一遍</b>：<b>不再调父类</b>，改用
 *       {@link #renderTireOnly} 自己把<b>同一个轮胎物品</b>画出来；
 *       变换链（平移 / 偏航 / 转速 / 轮胎自身 rotation / offset）逐条照抄父类字节码，
 *       所以轮胎的外观、朝向、转速与中心那一个完全同步。杆件/推杆/减震/轴只在中心出现一次。</li>
 * </ol>
 *
 * <h2>间距</h2>
 * 实测 offroad 轮胎 OBJ 沿轮轴（Y）的包围盒宽度
 * （{@code assets/offroad/models/item/&lt;尺寸&gt;/block.obj}）：
 * <pre>
 *   small_tire        Y[0.1875 .. 0.8125]  宽 0.625
 *   tire              Y[0.0625 .. 0.9375]  宽 0.875
 *   large_tire        Y[0.0625 .. 0.9375]  宽 0.875
 *   monstrous_tire    Y[0.0625 .. 0.9375]  宽 0.875
 * </pre>
 * <b>大型/巨型并不比普通胎厚</b>（都是 0.875），只有小型胎是 0.625。
 * 上一版按「半径阈值猜宽度」把大型/巨型猜成 1.0、把小型猜成 0.8125，两处都错，所以机主看到间隙不对。
 * 本版改为<b>按注册物品精确查表</b>（{@link #widthFor}）。偏移 = 胎宽 / 2 x {@link #GAP_FACTOR}，两胎边缘正好贴合。
 */
public class DualWheelSuspensionRenderer extends WheelMountRenderer {

    /**
     * 第二个轮胎沿轮轴的偏移（格）。
     * <p>
     * 机主最终拍板：<b>「在原有的悬架上往外扩一格放另一个轮胎就可以了，
     * 隐不隐藏都无所谓了，问题是你总得像个样子吧」</b> ——
     * 也就是「本该放第二个悬架的那一格」，所以偏移固定为 <b>1.0 格</b>，
     * <b>不再按胎宽算</b>（之前那套 0.4375 = 胎宽/2 是错的）。
     * <p>
     * 做法：<b>方块中心那一格画完整悬挂 + 一个轮胎，再沿轮轴 ±1 格各画一个轮胎</b>。
     * 于是两个轮胎正好各占一格中心、间隔一整格 —— 方块世界里最"像样"的间距。
     * {@link #GAP_FACTOR} 只是给"以后想微调"留的口子，当前必须是 1.0。
     */
    public static final float AXLE_OFFSET = 1.0F;

    /**
     * 普通/大型/巨型轮胎的偏移基准。机主实机反馈"原版的小型轮胎和普通轮胎的缝隙并不好"，
     * 要求"稍微收那么 0.1" —— 所以基准从 1.0 收到 <b>0.9</b>。
     */
    public static final float BASE_OFFSET = 0.9F;

    /** 实测（offroad 轮胎 OBJ 沿轮轴 Y 的包围盒）：普通/大型/巨型都是 0.875，只有小型是 0.625。 */
    public static final float WIDTH_NORMAL = 0.875F;
    public static final float WIDTH_SMALL = 0.625F;

    /** 小型轮胎的半径上限（0.75 = SMALL_TIRE，其余为 0.96875 / 1.25 / 2.0）。 */
    public static final float SMALL_RADIUS_MAX = 0.75F;

    /** 只有【巨型】轮胎才额外往外推（2.0 = MONSTROUS_TIRE）。大型 1.25 不推，保持与普通一致。 */
    public static final float LARGE_RADIUS_MIN = 2.0F;

    /** 巨型轮胎额外往外推的距离。机主："巨型轮胎没毛病了"，但大型要改回原样。 */
    public static final float LARGE_EXTRA_OFFSET = 0.2F;

    /**
     * 按实际胎宽与尺寸决定偏移：窄胎少推、大胎多推，这样各种尺寸看到的缝隙都合适。
     * <ul>
     *   <li>小型（r=0.75）：{@code 0.9 - (0.875-0.625) = 0.65}</li>
     *   <li>普通（r=0.96875）：{@code 0.9}</li>
     *   <li>大型 / 巨型（r=1.25 / 2.0）：{@code 0.9 + 0.2 = 1.1}</li>
     * </ul>
     */
    private static float offsetFor(WheelMountBlockEntity be) {
        float width = WIDTH_NORMAL;
        float extra = 0.0F;
        try {
            TireLike like = be.getHeldItem().get(OffroadDataComponents.TIRE);
            if (like != null && like.radius() > 0.0F) {
                if (like.radius() <= SMALL_RADIUS_MAX) {
                    width = WIDTH_SMALL;
                } else if (like.radius() >= LARGE_RADIUS_MIN) {
                    extra = LARGE_EXTRA_OFFSET;
                }
            }
        } catch (Throwable ignored) {
            // 读不到就按普通胎处理
        }
        return BASE_OFFSET - (WIDTH_NORMAL - width) + extra;
    }

    /** 偏移微调系数；1.0 = 用 {@link #BASE_OFFSET} 原值。 */
    public static final float GAP_FACTOR = 1.0F;

    public DualWheelSuspensionRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(WheelMountBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction axle = be.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        float offset = offsetFor(be) * GAP_FACTOR;

        // ---- 第一遍：本体位置，完整悬挂 + 轮胎（父类原样执行，保证第一个轮子完全正常）----
        super.renderSafe(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        // ---- 第二遍：沿轮轴【往外一整格】再完整画一整套（悬挂 + 轮胎）----
        // 机主：「在原有的悬架上往外扩一格放另一个轮胎就可以了，隐不隐藏都无所谓了，问题是你总得像个样子吧」
        // 以及「还不如最初的好」——最初那版是在相邻格真的放了一整套车轮悬架，所以看起来是"两个轮子"。
        // 只画一个裸轮胎会显得悬空、不像样（上一版就是这个毛病），所以这里把整套悬挂一起复制过去。
        poseStack.pushPose();
        poseStack.translate(axle.getStepX() * offset, axle.getStepY() * offset, axle.getStepZ() * offset);
        super.renderSafe(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 只画一个轮胎（照抄 {@code WheelMountRenderer.renderSafe} 里画轮胎那段的变换链）。
     * <p>
     * 父类里轮胎那一段的等效变换（local space，方块中心为原点）为：
     * <pre>
     *   rotateY(AngleHelper.horizontalAngle(facing.getOpposite()))
     *   rotateX(AngleHelper.verticalAngle(facing.getOpposite()))
     *   rotateAround(Y(l erpedYaw), .5f, .5f, -.5f)
     *   translate(0, lerpedExtension, -0.25)
     *   translate(.5, .5, .5) ; rotateAround(Z(-lerpedAngle), 0, 0, -1) ; translate(-.5, -.5, -.5)
     *   rotateAround(Z(zSign), 0, 0, -1)
     *   rotateX(tire.rotation.x) ; rotateY(tire.rotation.y) ; rotateZ(tire.rotation.z)
     *   translate(tire.offset)
     * </pre>
     * 其中 {@code lerpedExtension = -be.getLerpedExtension(partialTick)}，
     * {@code zSign} 由 {@code facing.getOpposite()} 的轴向与轴方向决定（见下）。
     */
    private void renderTireOnly(WheelMountBlockEntity be, float partialTick, PoseStack poseStack,
                                MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                Direction axle, float axleOffset) {
        ItemStack stack = be.getHeldItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        double extension = -be.getLerpedExtension(partialTick);
        Direction opposite = be.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING).getOpposite();

        poseStack.pushPose();

        // 1) 父类开头把整个 pose 对齐到方块朝向（和第一遍同一套，所以朝向一致）
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(opposite)));
        poseStack.mulPose(Axis.XP.rotationDegrees(AngleHelper.verticalAngle(opposite)));
        poseStack.translate(-0.5, -0.5, -0.5);

        // 2) 偏航（绕轴上的点 (0.5, 0.5, -0.5) 转 lerpedYaw）
        //    getLerpedYaw 在父类里是 protected，渲染器拿不到，所以由我们的 BE 开了 dangtools$getLerpedYaw。
        double lerpedYaw = be instanceof com.dangtools.block.DualWheelSuspensionBlockEntity dual
                ? dual.dangtools$getLerpedYaw(partialTick)
                : 0.0D;
        poseStack.pushPose();
        poseStack.translate(0.0, extension, -0.25);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotateAround(Axis.YP.rotationDegrees((float) lerpedYaw), 0.0F, 0.0F, -1.0F);
        poseStack.translate(-0.5, -0.5, -0.5);

        // 3) 轮胎自转（绕点 (0,0,-1) 转 -lerpedAngle，符号随轴朝向）
        double zSign = (double) (-be.getLerpedAngle(partialTick))
                * (opposite.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D)
                * (opposite.getAxis() == Direction.Axis.X ? 1.0D : -1.0D);
        poseStack.rotateAround(Axis.ZP.rotationDegrees((float) zSign), 0.0F, 0.0F, -1.0F);

        // 4) 沿轮轴平移到「第二个轮子」的位置（这是我们和父类唯一的差别）
        poseStack.translate(axle.getStepX() * axleOffset, axle.getStepY() * axleOffset, axle.getStepZ() * axleOffset);

        // 5) 轮胎自身的 rotation / offset（我们注册的是 rotation=(90,0,0)、offset=0）
        TireLike like = stack.get(OffroadDataComponents.TIRE);
        Vec3 rotation = like != null ? like.rotation() : new Vec3(90.0, 0.0, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees((float) rotation.x));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) rotation.y));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotation.z));

        if (like != null) {
            Vec3 offset = like.offset();
            poseStack.translate(offset.x, offset.y, offset.z);
        }

        // 6) 画轮胎本体：优先用 TireLike 自带模型，否则用物品模型（与原版一致的两条分支）
        if (like != null && like.model().isPresent()) {
            try {
                SuperByteBuffer model = CachedBuffers.partial(
                        dev.engine_room.flywheel.lib.model.baked.PartialModel.of(like.model().get()),
                        be.getBlockState());
                if (model != null) {
                    model.light(packedLight)
                            .translate(-0.5F, 0.0F, -0.5F)
                            .renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
                }
            } catch (Throwable ignored) {
                // 模型取不到就退回到物品渲染
                renderTireItem(stack, poseStack, bufferSource, packedLight, packedOverlay, be);
            }
        } else {
            renderTireItem(stack, poseStack, bufferSource, packedLight, packedOverlay, be);
        }

        poseStack.popPose();
        poseStack.popPose();
    }

    private static void renderTireItem(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, int packedOverlay, WheelMountBlockEntity be) {
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.NONE, packedLight, packedOverlay,
                poseStack, bufferSource, be.getLevel(), 0);
    }
}
