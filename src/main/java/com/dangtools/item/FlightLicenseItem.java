package com.dangtools.item;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * 飞行执照：
 * - 放在副手：充当不死图腾，且无限使用（不消耗）。
 * - 穿在胸甲槽：提供护甲/韧性 + 多种保护效果 + 荆棘反伤，并可作为鞘翅飞行。
 * - 由于护甲贴图为全透明，第一/第三人称都看不到身上穿戴了任何东西。
 */
public class FlightLicenseItem extends ArmorItem {

    public FlightLicenseItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, ArmorItem.Type.CHESTPLATE, properties);
    }

    /** 允许滑翔（当作鞘翅使用）。 */
    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true;
    }

    /** 滑翔不掉耐久（无限使用）。 */
    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return true;
    }
}
