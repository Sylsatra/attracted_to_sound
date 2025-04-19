package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;
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

        LOGGER.info("SoundAttractMod Initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("SoundAttractMod Common Setup");
        SoundAttractNetwork.init();
        LOGGER.info("Initialized SoundAttractNetwork");
        LOGGER.info("SoundAttractionEvents registered via annotation.");

        handleTaczIntegration();
    }

    private void handleTaczIntegration() {
        if (ModList.get().isLoaded("tacz")) {
            if (SoundAttractConfig.TACZ_ENABLED_CACHE) {
                LOGGER.info("TaCz mod detected and integration enabled. Registering event listeners.");
                try {
                    MinecraftForge.EVENT_BUS.register(com.example.soundattract.integration.TaczIntegrationEvents.class);
                } catch (Exception e) {
                    LOGGER.error("Failed to register TaCz integration event listener", e);
                } catch (NoClassDefFoundError e) {
                    LOGGER.error("TaCz integration class not found, disabling integration.", e);
                }
            } else {
                LOGGER.info("TaCz integration disabled in config.");
            }
        } else {
            LOGGER.info("TaCz mod not detected. Integration disabled.");
        }
    }

    @SubscribeEvent
    public void onModConfigEvent(ModConfigEvent.Loading event) {
        LOGGER.info("SoundAttractMod Config Loading: {}", event.getConfig().getFileName());
        if (event.getConfig().getSpec() == SoundAttractConfig.COMMON_SPEC) {
            SoundAttractConfig.bakeConfig();
        }
    }

    @SubscribeEvent
    public void onModConfigEvent(ModConfigEvent.Reloading event) {
        LOGGER.info("SoundAttractMod Config Reloading: {}", event.getConfig().getFileName());
        if (event.getConfig().getSpec() == SoundAttractConfig.COMMON_SPEC) {
            SoundAttractConfig.bakeConfig();
        }
    }
}
