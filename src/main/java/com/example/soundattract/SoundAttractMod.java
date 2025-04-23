package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.integration.VoiceChatIntegration;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC, "soundattract-common.toml");

        modEventBus.register(this); 

        handleTaczIntegration();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        SoundAttractNetwork.init();

        handleTaczIntegration();
    }

    private void handleTaczIntegration() {
        if (ModList.get().isLoaded("tacz")) {
            if (SoundAttractConfig.TACZ_ENABLED_CACHE) {
                try {
                    MinecraftForge.EVENT_BUS.register(com.example.soundattract.integration.TaczIntegrationEvents.class);
                } catch (Exception e) {
                    try {
                        MinecraftForge.EVENT_BUS.register(com.example.soundattract.integration.TaczIntegrationEvents.class);
                    } catch (NoClassDefFoundError e1) {
                    }
                } catch (NoClassDefFoundError e) {
                }
            }
        }
    }

    @SubscribeEvent
    public void onModConfigEvent(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SoundAttractConfig.COMMON_SPEC) {
            SoundAttractConfig.bakeConfig();
        }
    }

    @SubscribeEvent
    public void onModConfigEvent(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SoundAttractConfig.COMMON_SPEC) {
            SoundAttractConfig.bakeConfig();
        }
    }
}
