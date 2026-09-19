package com.dangtools;

import com.dangtools.tags.DangTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * 游戏内事件处理：
 * - 镰刀：破坏农作物 -> 掉落 x2，经验翻倍。
 * - 来自中国伟大的党（组合工具）：农作物/矿石 -> 掉落 x3、经验 x3。
 * - 飞行执照：副手充当不死图腾（无限使用）；胸甲提供多种保护效果 + 荆棘反伤。
 */
@EventBusSubscriber(modid = DangTools.MODID)
public class ModEvents {

    // ---------------- 收获/采矿加倍 ----------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        boolean sickle = main.is(Registration.SICKLE.get());
        boolean party = main.is(Registration.PARTY_TOOL.get());
        if (!sickle && !party) {
            return;
        }

        Level level = event.getLevel() instanceof Level l ? l : null;
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        // 党的动力：本身只掉 1 个；用「来自中国伟大的党」挖掘时有 40% 概率额外再掉 1 个
        if (party && state.is(Registration.PARTY_POWER.get())) {
            if (level instanceof ServerLevel serverLevel && serverLevel.random.nextFloat() < 0.40F) {
                Vec3 at = pos.getCenter();
                ItemEntity extra = new ItemEntity(serverLevel, at.x, at.y + 0.2, at.z,
                        new ItemStack(Registration.PARTY_POWER_ITEM.get()));
                extra.setDeltaMovement(0.0, 0.2, 0.0);
                serverLevel.addFreshEntity(extra);
            }
            return;
        }

        int multiplier = party ? 3 : 2;             // 镰刀 x2，组合工具 x3
        int extraCopies = multiplier - 1;           // 额外生成的副本数量

        boolean relevant = state.is(DangTags.CROPS) || (party && state.is(DangTags.ORE_HAMMERABLE));
        if (!relevant) {
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, main);
            if (!drops.isEmpty() && extraCopies > 0) {
                Vec3 center = pos.getCenter().subtract(0, -0.1, 0);
                for (int i = 0; i < extraCopies; i++) {
                    for (ItemStack drop : drops) {
                        ItemEntity entity = new ItemEntity(serverLevel, center.x, center.y, center.z, drop.copy());
                        entity.setDeltaMovement(
                                (level.random.nextDouble() - 0.5) * 0.25,
                                level.random.nextDouble() * 0.3 + 0.1,
                                (level.random.nextDouble() - 0.5) * 0.25);
                        serverLevel.addFreshEntity(entity);
                    }
                }
            }
            // 经验翻倍：以固定基础经验值（每份 3）乘以倍率。
            ExperienceOrb.award(serverLevel, pos.getCenter().subtract(0, -0.1, 0), multiplier * 3);
        }
    }

    // ---------------- 飞行执照：副手不死图腾 + 胸甲荆棘 ----------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }

        // 副手：不死图腾，且无限使用（不消耗物品）
        if (player.getOffhandItem().is(Registration.FLIGHT_LICENSE.get())) {
            if (event.getAmount() >= player.getHealth()) {
                event.setCanceled(true);
                player.setHealth(1.0F);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                player.level().broadcastEntityEvent(player, (byte) 35); // 不死图腾动画
            }
            return;
        }

        // 胸甲：荆棘反伤
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Registration.FLIGHT_LICENSE.get())) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity living && living != player) {
                living.hurt(player.damageSources().thorns(player), 3.0F);
            }
        }
    }

    // ---------------- 飞行执照：胸甲提供多种保护效果 ----------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Registration.FLIGHT_LICENSE.get())) {
            return;
        }
        refresh(player, MobEffects.DAMAGE_RESISTANCE, 1);
        refresh(player, MobEffects.FIRE_RESISTANCE, 0);
        refresh(player, MobEffects.WATER_BREATHING, 0);
        refresh(player, MobEffects.REGENERATION, 0);
    }

    private static void refresh(Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 30 || current.getAmplifier() < amplifier) {
            player.addEffect(new MobEffectInstance(effect, 100, amplifier, true, false, false));
        }
    }
}
