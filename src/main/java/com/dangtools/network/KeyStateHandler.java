package com.dangtools.network;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端的按键状态表：玩家 -> 最近一次收到的前进/后退/左/右按下状态。
 * <p>
 * 灯光系统（转向灯 / 刹车灯 / 车内灯）都从这里读键，从而做到"改完按键绑定后所有灯光都跟着变"。
 */
public final class KeyStateHandler {

    /** 一个玩家的四个键状态。 */
    public record Keys(boolean forward, boolean back, boolean left, boolean right) {
        public static final Keys NONE = new Keys(false, false, false, false);
    }

    private static final Map<UUID, Keys> STATES = new ConcurrentHashMap<>();

    private KeyStateHandler() {}

    public static void update(Player player, Keys keys) {
        if (player == null) {
            return;
        }
        if (keys.equals(Keys.NONE)) {
            STATES.remove(player.getUUID());
        } else {
            STATES.put(player.getUUID(), keys);
        }
    }

    public static Keys get(Player player) {
        return player == null ? Keys.NONE : STATES.getOrDefault(player.getUUID(), Keys.NONE);
    }

    public static void clear(Player player) {
        if (player != null) {
            STATES.remove(player.getUUID());
        }
    }
}
