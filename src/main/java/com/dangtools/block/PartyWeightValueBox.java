package com.dangtools.block;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.world.phys.Vec3;

/**
 * 党的分量的数值框定位：放在方块南面外侧（全立方体方块，无需朝向）。
 * 数值框的朝向由 Create 的数值框系统自动处理（Sided 默认朝上，安全）。
 */
public class PartyWeightValueBox extends ValueBoxTransform.Sided {

    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, 0.78125);
    }
}
