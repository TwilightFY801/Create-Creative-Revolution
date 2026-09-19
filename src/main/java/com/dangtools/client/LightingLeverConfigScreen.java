package com.dangtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 照明拉杆的按键配置界面（潜行右键拉杆打开）。
 * <p>
 * <b>改的就是那套内置绑定</b>：{@code DangKeybinds} 里的前进/后退/左/右四个 {@link KeyMapping}。
 * 用 {@link KeyMapping#setKey} 改键、{@code Options.save()} 写回磁盘；
 * 「恢复默认」用 {@link KeyMapping#resetMapping()}。
 * <p>
 * <b>为什么改完不需要通知服务端</b>：客户端负责把「物理键」翻译成「逻辑输入」
 * （前进/后退/左/右），只有逻辑输入会发给服务端（{@code KeyInputC2SPayload}）。
 * 所以改完绑定之后，所有灯光自动按新绑定判定。
 */
public class LightingLeverConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final List<KeyMapping> mappings = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    /** 正在等待玩家按键的那个绑定的下标；-1 = 没有在改键。 */
    private int listeningIndex = -1;

    public LightingLeverConfigScreen(Screen parent) {
        super(Component.translatable("dangtools.lighting_lever.config.title"));
        this.parent = parent;
        for (KeyMapping mapping : DangKeybinds.ALL) {
            mappings.add(mapping);
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        int centerX = this.width / 2;
        int y = this.height / 2 - (mappings.size() * ROW_HEIGHT) / 2 - 10;

        for (int i = 0; i < mappings.size(); i++) {
            final int index = i;
            Button button = Button.builder(Component.empty(), b -> beginListening(index))
                    .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addRenderableWidget(button);
            buttons.add(button);
            y += ROW_HEIGHT;
        }

        y += 10;
        addRenderableWidget(Button.builder(Component.translatable("dangtools.lighting_lever.config.reset"),
                        b -> resetDefaults())
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH / 2 - 2, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(centerX + 2, y, BUTTON_WIDTH / 2 - 2, BUTTON_HEIGHT)
                .build());

        refreshLabels();
    }

    private void beginListening(int index) {
        listeningIndex = index;
        refreshLabels();
    }

    private void refreshLabels() {
        for (int i = 0; i < buttons.size() && i < mappings.size(); i++) {
            KeyMapping mapping = mappings.get(i);
            Component label;
            if (i == listeningIndex) {
                label = Component.translatable(mapping.getName())
                        .append(Component.literal(": "))
                        .append(Component.translatable("dangtools.lighting_lever.config.press"));
            } else {
                label = Component.translatable(mapping.getName())
                        .append(Component.literal(": "))
                        .append(mapping.getTranslatedKeyMessage());
            }
            buttons.get(i).setMessage(label);
        }
        if (listeningIndex >= 0 && listeningIndex < mappings.size()) {
            setFocused(buttons.get(listeningIndex));
        }
    }

    private void resetDefaults() {
        // 恢复默认绑定（前进 W / 后退 S / 左 A / 右 D）。
        // 注意：KeyMapping 没有「把单个映射恢复默认」的实例方法（只有静态 resetMapping() 用来清缓存），
        // 所以这里把默认键直接写回去。
        DangKeybinds.FORWARD.setKey(InputConstants.Type.KEYSYM.getOrCreate(DangKeybinds.DEFAULT_FORWARD));
        DangKeybinds.BACK.setKey(InputConstants.Type.KEYSYM.getOrCreate(DangKeybinds.DEFAULT_BACK));
        DangKeybinds.LEFT.setKey(InputConstants.Type.KEYSYM.getOrCreate(DangKeybinds.DEFAULT_LEFT));
        DangKeybinds.RIGHT.setKey(InputConstants.Type.KEYSYM.getOrCreate(DangKeybinds.DEFAULT_RIGHT));
        listeningIndex = -1;
        KeyMapping.resetMapping();
        saveOptions();
        refreshLabels();
    }

    private void saveOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            minecraft.options.save();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningIndex < 0 || listeningIndex >= mappings.size()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        KeyMapping target = mappings.get(listeningIndex);

        if (keyCode == InputConstants.KEY_ESCAPE) {
            listeningIndex = -1;
            refreshLabels();
            return true;
        }

        if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
            target.setKey(InputConstants.UNKNOWN);
        } else {
            target.setKey(InputConstants.getKey(keyCode, scanCode));
        }
        unbindConflicts(target);
        KeyMapping.resetMapping();
        saveOptions();
        listeningIndex = -1;
        refreshLabels();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 改键等待中时，点鼠标也能绑定鼠标键（和原版按键设置一致）
        if (listeningIndex >= 0 && listeningIndex < mappings.size()) {
            mappings.get(listeningIndex).setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            unbindConflicts(mappings.get(listeningIndex));
            KeyMapping.resetMapping();
            saveOptions();
            listeningIndex = -1;
            refreshLabels();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 现在**什么都不做**。
     * <p>
     * 以前这里会遍历 {@code minecraft.options.keyMappings}，把绑到同一个键的其它映射
     * 全部设为 {@link InputConstants#UNKNOWN} —— 而本模组这四个键默认就是 W / A / S / D，
     * 于是玩家在拉杆界面改一次键，就会**把原版的移动键 W/A/S/D 一起解绑掉**，
     * 直接影响游戏本体的按键设置（机主实测反馈的问题）。
     * <p>
     * 本模组的灯光键是<b>只读</b>的（只查询是否按下，不 consume 事件），
     * 所以和原版移动键共用同一个键完全没问题，不需要"解冲突"。
     */
    @SuppressWarnings("unused")
    private void unbindConflicts(KeyMapping target) {
        // intentionally empty: 不再改动游戏本体的任何按键绑定
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2,
                this.height / 2 - (mappings.size() * ROW_HEIGHT) / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("dangtools.lighting_lever.config.hint"),
                this.width / 2, this.height - 28, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        if (listeningIndex >= 0) {
            listeningIndex = -1;
        }
        saveOptions();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
