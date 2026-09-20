package com.dangtools.world;

import com.dangtools.DangTools;
import com.dangtools.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * 隐藏彩蛋：在<b>每个存档的主世界</b>坐标 <b>(12, 8, 1)</b> 埋一个<b>大箱子（双箱）</b>。
 *
 * <p>坐标来自机主的生日 —— <b>一二年 8 月 1 日</b>。
 *
 * <p>箱子里装：
 * <ul>
 *   <li>本模组<b>全部物品</b>各一份（自动遍历物品注册表，以后加物品也会自动进箱）；</li>
 *   <li>黄铜机壳 ×64、创造物理手柄（Simulated）×64、大齿轮 ×64、小齿轮 ×64、传动杆 ×64。</li>
 * </ul>
 *
 * <p>只放<b>一次</b>：用 {@link SavedData} 记标记，玩家挖走之后不会再刷新。
 */
@EventBusSubscriber(modid = DangTools.MODID)
public final class BirthdayChest {

    /** 彩蛋坐标：机主生日 12 / 08 / 01。 */
    public static final BlockPos POS = new BlockPos(12, 8, 1);

    private BirthdayChest() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;   // 只放主世界
        }
        BlockPos chunkOrigin = event.getChunk().getPos().getWorldPosition();
        if (chunkOrigin.getX() != 0 || chunkOrigin.getZ() != 0) {
            return;   // 只关心 (0,0) 区块
        }

        Flag flag = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Flag::new, Flag::load), "dangtools_birthday_chest");
        if (flag.placed) {
            return;
        }
        flag.placed = true;
        flag.setDirty();

        placeChest(level);
        DangTools.LOGGER.info("[dangtools] 生日彩蛋已放置于 {}", POS);
    }

    private static void placeChest(ServerLevel level) {
        // ---- 大箱子（双箱）：(12,8,1) + (13,8,1) ----
        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);

        level.setBlock(POS, left, 3);
        level.setBlock(POS.east(), right, 3);

        BlockEntity be = level.getBlockEntity(POS);
        if (!(be instanceof ChestBlockEntity chest)) {
            return;
        }

        int slot = 0;
        // 1) 本模组的全部物品
        for (var holder : Registration.ITEMS.getEntries()) {
            Item item = holder.get();
            if (item != null && slot < chest.getContainerSize()) {
                chest.setItem(slot++, new ItemStack(item));
            }
        }
        // 2) 额外的整组物资
        String[] extras = {
                "create:brass_casing",
                "simulated:creative_physics_staff",
                "create:large_cogwheel",
                "create:cogwheel",
                "create:shaft",
        };
        for (String id : extras) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if (slot < chest.getContainerSize()) {
                chest.setItem(slot++, new ItemStack(item, 64));
            }
        }
        chest.setChanged();
    }

    /** 存档标记：彩蛋只放一次。 */
    public static class Flag extends SavedData {
        boolean placed;

        static Flag load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Flag f = new Flag();
            f.placed = tag.getBoolean("placed");
            return f;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            tag.putBoolean("placed", placed);
            return tag;
        }
    }
}
