package com.dangtools;

import com.dangtools.block.DualWheelSuspensionBlock;
import com.dangtools.block.LightingLeverBlockEntity;
import com.dangtools.block.DualWheelSuspensionBlockEntity;
import com.dangtools.block.FlagBlock;
import com.dangtools.block.FlagBlockEntity;
import com.dangtools.block.PartyPowerBlock;
import com.dangtools.block.PartyPowerBlockEntity;
import com.dangtools.block.PartyWeightBlock;
import com.dangtools.block.PartyWeightBlockEntity;
import com.dangtools.block.TrimBallastBlock;
import com.dangtools.block.TrimBallastBlockEntity;
import com.dangtools.item.FlightLicenseItem;
import com.dangtools.item.HammerItem;
import com.dangtools.item.MassScannerItem;
import com.dangtools.item.PartyToolItem;
import com.dangtools.item.SickleItem;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.content.items.tire.TireItem;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

public class Registration {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DangTools.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DangTools.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DangTools.MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, DangTools.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DangTools.MODID);

    // ---------- 核心工具（仅铁锭 + 木棍，仅铁制层级） ----------
    public static final DeferredItem<HammerItem> HAMMER =
            ITEMS.registerItem("hammer", HammerItem::new, toolProps(HammerItem.DURABILITY, 6.0));
    public static final DeferredItem<SickleItem> SICKLE =
            ITEMS.registerItem("sickle", SickleItem::new, toolProps(SickleItem.DURABILITY, 6.0));
    public static final DeferredItem<PartyToolItem> PARTY_TOOL =
            ITEMS.registerItem("party_sickle_hammer", PartyToolItem::new, toolProps(999999, 9998.0));

    // ---------- 飞行执照（国旗贴图；副手=不死图腾；胸甲=保护+荆棘+鞘翅；全透明不显示） ----------
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FLIGHT_LICENSE_MATERIAL =
            ARMOR_MATERIALS.register("flight_license", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.CHESTPLATE, 9),
                    30,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "flight_license"))),
                    4.0F,
                    0.1F));
    public static final DeferredItem<FlightLicenseItem> FLIGHT_LICENSE =
            ITEMS.registerItem("flight_license",
                    props -> new FlightLicenseItem(FLIGHT_LICENSE_MATERIAL, props),
                    new Item.Properties().stacksTo(1));

    // ---------- 党的动力（创造马达的直接复制，方块实体复用创造马达的 MOTOR 类型） ----------
    public static final DeferredBlock<PartyPowerBlock> PARTY_POWER =
            BLOCKS.register("party_power",
                    () -> new PartyPowerBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> PARTY_POWER_ITEM =
            ITEMS.registerItem("party_power", props -> new BlockItem(PARTY_POWER.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartyPowerBlockEntity>> PARTY_POWER_BE =
            BLOCK_ENTITIES.register("party_power",
                    () -> BlockEntityType.Builder.<PartyPowerBlockEntity>of(
                                    (pos, state) -> new PartyPowerBlockEntity(blockEntityType("party_power"), pos, state),
                                    PARTY_POWER.get())
                            .build(null));

    // ---------- 党的分量（创造马达外观、黄色机壳、无传动杆；功能为“可调节重力”，单通道） ----------
    public static final DeferredBlock<PartyWeightBlock> PARTY_WEIGHT =
            BLOCKS.register("party_weight",
                    () -> new PartyWeightBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F)));
    public static final DeferredItem<BlockItem> PARTY_WEIGHT_ITEM =
            ITEMS.registerItem("party_weight", props -> new BlockItem(PARTY_WEIGHT.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartyWeightBlockEntity>> PARTY_WEIGHT_BE =
            BLOCK_ENTITIES.register("party_weight",
                    () -> BlockEntityType.Builder.<PartyWeightBlockEntity>of(
                                    (pos, state) -> new PartyWeightBlockEntity(blockEntityType("party_weight"), pos, state),
                                    PARTY_WEIGHT.get())
                            .build(null));

    // ---------- 国旗（放在竖直传动杆上，范围内满级信标增益 64×64×64） ----------
    public static final DeferredBlock<FlagBlock> FLAG =
            BLOCKS.register("flag",
                    () -> new FlagBlock(BlockBehaviour.Properties.of().instabreak().noOcclusion().noCollission()));
    public static final DeferredItem<BlockItem> FLAG_ITEM =
            ITEMS.registerItem("flag", props -> new BlockItem(FLAG.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlagBlockEntity>> FLAG_BE =
            BLOCK_ENTITIES.register("flag",
                    () -> BlockEntityType.Builder.<FlagBlockEntity>of(
                                    (pos, state) -> new FlagBlockEntity(blockEntityType("flag"), pos, state),
                                    FLAG.get())
                            .build(null));

    // ---------- 双人悬架 ----------
    // 外观与原版车轮悬架完全一致（复用 Offroad 的模型/贴图，不压缩不变窄）。
    // 唯一差异：装一个普通轮胎时，会在正前方再延伸一格生成第二个「真正的」车轮悬架并装上同一款轮胎，
    // 两个轮子都有动力、一起转（详见 DualWheelSuspensionBlock）。
    public static final DeferredBlock<DualWheelSuspensionBlock> DUAL_WHEEL_SUSPENSION =
            BLOCKS.register("dual_wheel_suspension",
                    () -> new DualWheelSuspensionBlock(
                            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> DUAL_WHEEL_SUSPENSION_ITEM =
            ITEMS.registerItem("dual_wheel_suspension",
                    props -> new BlockItem(DUAL_WHEEL_SUSPENSION.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DualWheelSuspensionBlockEntity>> DUAL_WHEEL_SUSPENSION_BE =
            BLOCK_ENTITIES.register("dual_wheel_suspension",
                    () -> BlockEntityType.Builder.<DualWheelSuspensionBlockEntity>of(
                                    (pos, state) -> new DualWheelSuspensionBlockEntity(
                                            blockEntityType("dual_wheel_suspension"), pos, state),
                                    DUAL_WHEEL_SUSPENSION.get())
                            .build(null));

    // ---------- 微调配重块（精细配平：0~100 kpg，1 格 = 1 kpg） ----------
    // 独立的方块类与方块实体（属性范围 0~100，见 TrimBallastBlock），
    // 与重力方块（0~1000）完全隔离、互不影响。
    public static final DeferredBlock<TrimBallastBlock> TRIM_BALLAST =
            BLOCKS.register("trim_ballast",
                    () -> new TrimBallastBlock(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> TRIM_BALLAST_ITEM =
            ITEMS.registerItem("trim_ballast",
                    props -> new BlockItem(TRIM_BALLAST.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrimBallastBlockEntity>> TRIM_BALLAST_BE =
            BLOCK_ENTITIES.register("trim_ballast",
                    () -> BlockEntityType.Builder.<TrimBallastBlockEntity>of(
                                    (pos, state) -> new TrimBallastBlockEntity(
                                            blockEntityType("trim_ballast"), pos, state),
                                    TRIM_BALLAST.get())
                            .build(null));

    // ---------- 质量探测器（物品） ----------
    public static final DeferredItem<MassScannerItem> MASS_SCANNER =
            ITEMS.registerItem("mass_scanner", MassScannerItem::new, new Item.Properties());

    // ==========================================================================
    //  灯光系统（4/4 第一批 + 第二批）
    //  统一用 com.dangtools.lighting.DangLightBlock + DangLightBlockEntity 实现，
    //  亮度存在方块状态属性 level(0~15) 里，光照引擎查询是 O(1)。
    // ==========================================================================

    // ---------- 照明拉杆（总开关；右键拉动 / 潜行右键改按键） ----------
    public static final DeferredBlock<com.dangtools.lighting.LightingLeverBlock> LIGHTING_LEVER =
            BLOCKS.register("lighting_lever",
                    () -> new com.dangtools.lighting.LightingLeverBlock(
                            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> LIGHTING_LEVER_ITEM =
            ITEMS.registerItem("lighting_lever",
                    props -> new BlockItem(LIGHTING_LEVER.get(), props), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightingLeverBlockEntity>> LIGHTING_LEVER_BE =
            BLOCK_ENTITIES.register("lighting_lever",
                    () -> BlockEntityType.Builder.<LightingLeverBlockEntity>of(
                                    (pos, state) -> new LightingLeverBlockEntity(
                                            blockEntityType("lighting_lever"), pos, state),
                                    LIGHTING_LEVER.get())
                            .build(null));

    // ---------- 所有灯共用一个方块实体类型（在下面所有灯方块声明完之后再注册，见 LIGHT_BLOCKS_BE） ----------

    // ---------- 大灯（白色 15，开启时在物理结构前方整片生成光锥光方块） ----------
    public static final DeferredBlock<com.dangtools.lighting.DangLightBlock> HEADLIGHT =
            BLOCKS.register("headlight",
                    () -> com.dangtools.lighting.DangLightBlock.headlight(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> HEADLIGHT_ITEM =
            ITEMS.registerItem("headlight", props -> new BlockItem(HEADLIGHT.get(), props), new Item.Properties());

    // ---------- 刹车灯（拉杆开 = 常亮 10；后退键 = 15） ----------
    public static final DeferredBlock<com.dangtools.lighting.DangLightBlock> BRAKE_LIGHT =
            BLOCKS.register("brake_light",
                    () -> com.dangtools.lighting.DangLightBlock.brakeLight(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> BRAKE_LIGHT_ITEM =
            ITEMS.registerItem("brake_light", props -> new BlockItem(BRAKE_LIGHT.get(), props), new Item.Properties());

    // ---------- 左 / 右转向灯（按住对应键亮 15，黄色；不需要拉杆） ----------
    public static final DeferredBlock<com.dangtools.lighting.DangLightBlock> LEFT_TURN_SIGNAL =
            BLOCKS.register("left_turn_signal",
                    () -> com.dangtools.lighting.DangLightBlock.leftTurnSignal(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> LEFT_TURN_SIGNAL_ITEM =
            ITEMS.registerItem("left_turn_signal",
                    props -> new BlockItem(LEFT_TURN_SIGNAL.get(), props), new Item.Properties());

    public static final DeferredBlock<com.dangtools.lighting.DangLightBlock> RIGHT_TURN_SIGNAL =
            BLOCKS.register("right_turn_signal",
                    () -> com.dangtools.lighting.DangLightBlock.rightTurnSignal(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> RIGHT_TURN_SIGNAL_ITEM =
            ITEMS.registerItem("right_turn_signal",
                    props -> new BlockItem(RIGHT_TURN_SIGNAL.get(), props), new Item.Properties());

    // ---------- 车内照明灯（14；按 WASD → 缓慢变暗，松开 → 缓慢变亮） ----------
    public static final DeferredBlock<com.dangtools.lighting.DangLightBlock> INTERIOR_LIGHT =
            BLOCKS.register("interior_light",
                    () -> com.dangtools.lighting.DangLightBlock.interiorLight(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> INTERIOR_LIGHT_ITEM =
            ITEMS.registerItem("interior_light",
                    props -> new BlockItem(INTERIOR_LIGHT.get(), props), new Item.Properties());

    // ==========================================================================
    //  五色氛围灯（red_/purple_/yellow_/green_/blue_ambient_light）已按机主要求<b>整体移除</b>。
    //  仪表 5 件（高度计/姿态仪/汽笛/车牌/里程表）此前也已移除。
    //  灯光系统（拉杆/大灯/刹车灯/左右转向灯/车内灯）与其余全部内容不受影响。
    // ==========================================================================

    // ---------- 灯的方块实体类型（放在所有灯方块声明完之后，保证引用已初始化） ----------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.dangtools.lighting.DangLightBlockEntity>> LIGHTING_LIGHT_BE =
            BLOCK_ENTITIES.register("lighting_light",
                    () -> BlockEntityType.Builder.<com.dangtools.lighting.DangLightBlockEntity>of(
                                    (pos, state) -> new com.dangtools.lighting.DangLightBlockEntity(
                                            blockEntityType("lighting_light"), pos, state),
                                    HEADLIGHT.get(),
                                    BRAKE_LIGHT.get(),
                                    LEFT_TURN_SIGNAL.get(),
                                    RIGHT_TURN_SIGNAL.get(),
                                    INTERIOR_LIGHT.get())
                            .build(null));

    // ---------- 高抓地轮胎 / 漂移轮胎（各 4 种尺寸，共 8 个） ----------
    // 尺寸沿用 offroad 的 TireLike 预设半径：普通 0.96875 / 小型 0.75 / 大型 1.25 / 巨型 2.0，
    // 旋转与偏移沿用预设（Vec3(90,0,0) + ZERO），模型留空用默认外观。
    // 摩擦由 minFriction 决定：高抓地 = 2.0（原版默认 0.0），漂移 = -1.0（更易打滑）。
    public static final float GRIP_FRICTION = 2.0F;
    public static final float DRIFT_FRICTION = -1.0F;

    private static Item.Properties tire(float radius, float friction) {
        return new Item.Properties().component(OffroadDataComponents.TIRE,
                new TireLike(radius, new net.minecraft.world.phys.Vec3(90.0, 0.0, 0.0),
                        net.minecraft.world.phys.Vec3.ZERO, java.util.Optional.empty(), friction));
    }

    public static final DeferredItem<TireItem> GRIP_TIRE =
            ITEMS.registerItem("grip_tire", TireItem::new, tire(0.96875F, GRIP_FRICTION));
    public static final DeferredItem<TireItem> SMALL_GRIP_TIRE =
            ITEMS.registerItem("small_grip_tire", TireItem::new, tire(0.75F, GRIP_FRICTION));
    public static final DeferredItem<TireItem> LARGE_GRIP_TIRE =
            ITEMS.registerItem("large_grip_tire", TireItem::new, tire(1.25F, GRIP_FRICTION));
    public static final DeferredItem<TireItem> MONSTROUS_GRIP_TIRE =
            ITEMS.registerItem("monstrous_grip_tire", TireItem::new, tire(2.0F, GRIP_FRICTION));

    public static final DeferredItem<TireItem> DRIFT_TIRE =
            ITEMS.registerItem("drift_tire", TireItem::new, tire(0.96875F, DRIFT_FRICTION));
    public static final DeferredItem<TireItem> SMALL_DRIFT_TIRE =
            ITEMS.registerItem("small_drift_tire", TireItem::new, tire(0.75F, DRIFT_FRICTION));
    public static final DeferredItem<TireItem> LARGE_DRIFT_TIRE =
            ITEMS.registerItem("large_drift_tire", TireItem::new, tire(1.25F, DRIFT_FRICTION));
    public static final DeferredItem<TireItem> MONSTROUS_DRIFT_TIRE =
            ITEMS.registerItem("monstrous_drift_tire", TireItem::new, tire(2.0F, DRIFT_FRICTION));

    // ---------- 创造模式物品栏：一个页签，内部两个横幅分区 ----------
    // 分区内容由 CreativeModeTabMixin + DangCreativeTabSections 构建：
    //   分区一「革命：中国共产党」= 锤子 / 镰刀 / 来自伟大的党 / 国旗 / 飞行执照
    //   分区二「机械动力：创意革命」= 高级马达 / 重力方块
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> REVOLUTION_TAB =
            CREATIVE_TABS.register("creative_revolution", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DangTools.MODID))
                    .icon(() -> new ItemStack(PARTY_WEIGHT_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(HAMMER.get());
                        output.accept(SICKLE.get());
                        output.accept(PARTY_TOOL.get());
                        output.accept(FLAG_ITEM.get());
                        output.accept(FLIGHT_LICENSE.get());
                        output.accept(PARTY_POWER_ITEM.get());
                        output.accept(PARTY_WEIGHT_ITEM.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }

    /** 方块实体创建时从注册表取得本类型（此时类型已注册完成）。 */
    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> blockEntityType(String id) {
        return (BlockEntityType<T>) (BlockEntityType<?>) BuiltInRegistries.BLOCK_ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, id));
    }

    /**
     * 通用工具属性：耐久 + 攻击伤害。
     * 攻击伤害作为“基础 + 加成”的一部分：base 玩家攻击为 1，因此传入 6.0 = 总 7（比铁剑 4 高 3），
     * 传入 9998.0 = 总 9999。
     */
    private static Item.Properties toolProps(int durability, double attackDamage) {
        return new Item.Properties()
                .durability(durability)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "attack_damage"),
                                        attackDamage, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "attack_speed"),
                                        -2.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
    }
}
