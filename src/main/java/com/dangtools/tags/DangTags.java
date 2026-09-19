package com.dangtools.tags;

import com.dangtools.DangTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class DangTags {
    /** 可以被锤子右键直接采集的矿石（含深板岩与各维度矿石）。 */
    public static final TagKey<Block> ORE_HAMMERABLE = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "ore_hammerable"));

    /** 镰刀/组合工具可“加倍收获”的农作物（破坏时掉落翻倍）。 */
    public static final TagKey<Block> CROPS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "crops"));
}
