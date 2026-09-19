package com.dangtools.item;

import net.minecraft.world.item.Item;

/**
 * 镰刀：收获农作物时掉落物翻倍（含经验），攻击伤害比铁剑高 3 点。
 * 仅用铁锭与木棍合成，仅存在于铁制层级。
 */
public class SickleItem extends Item {

    public static final int DURABILITY = 600;

    public SickleItem(Properties properties) {
        super(properties);
    }
}
