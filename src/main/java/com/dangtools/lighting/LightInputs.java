package com.dangtools.lighting;

/**
 * 灯光系统认的四个「逻辑输入」。<b>不是键盘按键本身</b>。
 * <p>
 * 客户端在 {@code DangKeybinds} 里读四个按键的物理状态，把它翻译成这套逻辑值再发到服务端；
 * 服务端（和客户端复读）只认这四个逻辑值。这样「照明拉杆的配置界面改了按键绑定」之后，
 * 所有灯光自动跟着新绑定走 —— 因为改的是「哪个物理键对应哪个逻辑输入」这一段。
 */
public final class LightInputs {

    public static final LightInputs NONE = new LightInputs(false, false, false, false);

    private final boolean forward;
    private final boolean back;
    private final boolean left;
    private final boolean right;

    public LightInputs(boolean forward, boolean back, boolean left, boolean right) {
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
    }

    public boolean forward() {
        return forward;
    }

    public boolean back() {
        return back;
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    /** 任意一个方向键被按住（车内灯用它判断「有人在操作」）。 */
    public boolean any() {
        return forward || back || left || right;
    }

    public boolean same(LightInputs other) {
        return other != null
                && forward == other.forward
                && back == other.back
                && left == other.left
                && right == other.right;
    }

    @Override
    public String toString() {
        return "LightInputs[f=" + forward + ",b=" + back + ",l=" + left + ",r=" + right + "]";
    }
}
