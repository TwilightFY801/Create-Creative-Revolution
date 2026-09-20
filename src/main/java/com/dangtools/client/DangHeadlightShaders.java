package com.dangtools.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * 大灯光锥的<b>核心着色器</b>（<b>照 HandheldMoon 的 {@code beam_cone} 抄的</b>，机主要求直接抄）。
 *
 * <p>着色器资源：{@code assets/shaders/core/headlight_cone.fsh} + {@code .json}
 * （顶点格式 {@code position_tex_color}）。UV 约定：
 * <b>{@code texCoord0.x = 沿长度 0..1}、{@code texCoord0.y = 沿圆周 0..1}</b>。
 *
 * <p>已按机主要求把范围放大：{@code uRange} 14 → 90，{@code uHalfAngle} 28° → 45°。
 */
public final class DangHeadlightShaders {

    private static ShaderInstance cone;

    private DangHeadlightShaders() {}

    /** 由 {@code ClientSetup} 在 {@link RegisterShadersEvent} 里调用。 */
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("dangtools", "headlight_cone"),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> {
                        cone = shader;
                        com.dangtools.DangTools.LOGGER.info("[dangtools] 大灯光锥着色器加载成功");
                    });
        com.dangtools.DangTools.LOGGER.info("[dangtools] 正在注册大灯光锥着色器 dangtools:headlight_cone");
        } catch (Exception e) {
            com.dangtools.DangTools.LOGGER.error("[dangtools] 大灯光锥着色器【注册失败】", e);
        }
    }

    /** 拿不到就返回 null（着色器没编译成功时渲染器直接跳过，不影响游戏）。 */
    public static ShaderInstance cone() {
        return cone;
    }

    private static net.minecraft.client.renderer.RenderType coneRenderType;

    /**
     * 用我们的着色器构造 RenderType。
     * <p>
     * <b>为什么必须走 RenderType</b>：之前用"手动 {@code shader.apply()} + BufferBuilder + BufferUploader"
     * 那条路，矩阵/渲染状态没配对，结果<b>画不出来</b>。走 RenderType 后，
     * 投影/模型视图矩阵、混合、深度、剔除都由原版处理 —— 这是被验证过能出画面的路子。
     */
    public static net.minecraft.client.renderer.RenderType coneType() {
        if (coneRenderType == null) {
            net.minecraft.client.renderer.RenderType.CompositeState state =
                    net.minecraft.client.renderer.RenderType.CompositeState.builder()
                            .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(
                                    DangHeadlightShaders::cone))
                            .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
                            .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                            .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.LEQUAL_DEPTH_TEST)
                            .createCompositeState(false);
            coneRenderType = net.minecraft.client.renderer.RenderType.create(
                    "dangtools_headlight_cone",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    1536, false, true, state);
        }
        return coneRenderType;
    }
}
