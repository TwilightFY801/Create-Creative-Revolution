package com.dangtools.client;

import com.dangtools.lighting.DangLightBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 大灯的<b>纯渲染丁达尔光锥</b>。
 *
 * <h2>做法</h2>
 * 用<b>自定义核心着色器</b>（{@code dangtools:headlight_cone}，照 HandheldMoon 的 {@code beam_cone} 抄的）
 * 画一个圆台光锥，<b>走原版 RenderType</b>（矩阵 / 混合 / 剔除都交给原版处理，可靠）。
 *
 * <h2>为什么是"纯渲染"</h2>
 * 任何基于光照计算的方案（主世界放光方块 / LambDynamicLights）<b>光源一移动就触发区块光照重建 → 很卡</b>，
 * 而且 LDL 不提供体积光。纯渲染光束不写方块、不碰光照引擎 → 零区块重建 → 不卡。
 *
 * <h2>UV 约定（与 headlight_cone.fsh 一致）</h2>
 * {@code texCoord0.x = 沿长度 0..1}、{@code texCoord0.y = 沿圆周 0..1}。
 */
public class DangHeadlightBeamRenderer implements BlockEntityRenderer<DangLightBlockEntity> {

    /** 光锥长度（格）——机主要求"更大"，纯渲染不吃性能，可以放心给大。 */
    private static final float LENGTH = 200.0F;
    /** 出光口半径 / 远端半径（格）：远端很大 -> 扇形，不是光棒。 */
    private static final float NEAR_RADIUS = 0.18F;
    private static final float FAR_RADIUS = 110.0F;
    /** 出光口相对方块中心往前推一点。 */
    private static final float MOUTH_OFFSET = 0.52F;
    /** 圆锥用多少段近似。 */
    private static final int SEGMENTS = 20;

    public DangHeadlightBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DangLightBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // ① 灭灯不画
        if (be.lightLevelForRender() <= 0) {
            return;
        }
        // ② ★ 只有【大灯】这一个方块才画光锥（其他灯共用同一个 BE 类型，一律不画）
        if (be.getBlockState().getBlock() != com.dangtools.Registration.HEADLIGHT.get()) {
            return;
        }

        Direction facing;
        try {
            facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        } catch (Throwable ignored) {
            return;
        }

        ShaderInstance shader = DangHeadlightShaders.cone();
        if (shader == null) {
            return;   // 着色器没加载成功（正常情况不会到这里）
        }

        // ---- 光轴：方块朝向的反方向 + 瞄准偏角（潜行+滚轮调的就是它）----
        Vector3f dir = new Vector3f(-facing.getStepX(), 0.0F, -facing.getStepZ()).normalize();
        Vector3f worldUp = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f right = new Vector3f(dir).cross(worldUp);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();

        dir.rotateY((float) Math.toRadians(be.aimYaw()));
        right.set(dir).cross(worldUp);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(dir).normalize();
        if (be.aimPitch() != 0.0F) {
            dir.rotateAxis((float) Math.toRadians(be.aimPitch()), right.x, right.y, right.z).normalize();
            up.set(right).cross(dir).normalize();
        }

        // 出光口中心
        Vector3f mouth = new Vector3f(dir).mul(MOUTH_OFFSET);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        Matrix4f matrix = poseStack.last().pose();

        // ---- 上传自定义 uniform（留在 ShaderInstance 上，批次刷新时生效）----
        shader.safeGetUniform("uPalCount").set(1.0F);
        shader.safeGetUniform("uPal0").set(1.0F, 0.98F, 0.90F);
        shader.safeGetUniform("uUseOverride").set(0.0F);
        shader.safeGetUniform("uCenterAlpha").set(0.26F);
        shader.safeGetUniform("uEdgeAlpha").set(0.03F);
        shader.safeGetUniform("uNoiseAmp").set(0.35F);
        shader.safeGetUniform("uRange").set(LENGTH);
        shader.safeGetUniform("uHalfAngle").set(1.0471976F);
        shader.safeGetUniform("uSizeScale").set(1.0F);

        // ---- 走原版 RenderType 发顶点（矩阵/混合交给原版）----
        VertexConsumer vc = bufferSource.getBuffer(DangHeadlightShaders.coneType());

        Vector3f coneTip = new Vector3f(mouth);
        Vector3f coneEnd = new Vector3f(mouth).fma(LENGTH, dir);
        Vector3f prevNear = ring(coneTip, right, up, NEAR_RADIUS, 0);
        Vector3f prevFar = ring(coneEnd, right, up, FAR_RADIUS, 0);
        for (int i = 1; i <= SEGMENTS; i++) {
            Vector3f curNear = ring(coneTip, right, up, NEAR_RADIUS, i);
            Vector3f curFar = ring(coneEnd, right, up, FAR_RADIUS, i);
            float thetaA = (i - 1) / (float) SEGMENTS;
            float thetaB = i / (float) SEGMENTS;
            coneVertex(vc, matrix, prevNear, 0.0F, thetaA, packedOverlay);
            coneVertex(vc, matrix, curNear, 0.0F, thetaB, packedOverlay);
            coneVertex(vc, matrix, curFar, 1.0F, thetaB, packedOverlay);
            coneVertex(vc, matrix, prevFar, 1.0F, thetaA, packedOverlay);
            prevNear = curNear;
            prevFar = curFar;
        }

        poseStack.popPose();
    }

    /** 光锥顶点：u = t(沿长度)、v = theta(沿圆周) —— 与 headlight_cone.fsh 的 UV 约定一致。 */
    private static void coneVertex(VertexConsumer vc, Matrix4f matrix,
                                   Vector3f p, float t, float theta, int overlay) {
        vc.addVertex(matrix, p.x, p.y, p.z)
                .setColor(255, 255, 255, 255)
                .setUv(t, theta)
                .setOverlay(overlay)
                .setLight(0xF000F0);   // 自发光，忽略环境亮度
    }

    /** 圆锥横截面上第 index 个点。 */
    private static Vector3f ring(Vector3f centre, Vector3f right, Vector3f up, float radius, int index) {
        double angle = index * (Math.PI * 2.0D / SEGMENTS);
        float cx = (float) Math.cos(angle);
        float cy = (float) Math.sin(angle);
        return new Vector3f(centre).fma(radius * cx, right).fma(radius * cy, up);
    }

    @Override
    public boolean shouldRenderOffScreen(DangLightBlockEntity be) {
        return true;   // 光锥很长，别因为方块在屏幕外就不画
    }

    /**
     * ★ 关键：把<b>渲染包围盒</b>放大到能盖住整条光锥。
     * <p>
     * 默认包围盒是方块自己的 1×1×1，一旦那个小盒子被移出视锥（例如玩家顺着光柱往<b>上看</b>、
     * 灯本身滑出视野），原版就会<b>整个跳过这个渲染器</b> → 光柱凭空消失。
     * 放大到 {@code ±(LENGTH+16)} 后，只要光柱还有一部分在视野里就会渲染。
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(DangLightBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(LENGTH + 16.0D);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
