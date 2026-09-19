package com.dangtools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DangTools.MODID)
public class DangTools {
    public static final String MODID = "dangtools";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public DangTools(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("DangTools (党的工具) loading. 不忘初心，牢记使命。");
        Registration.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(com.dangtools.client.ClientSetup::registerRenderers);
            modEventBus.addListener(com.dangtools.client.ClientSetup::copyMotorVisual);
        }
    }
}
