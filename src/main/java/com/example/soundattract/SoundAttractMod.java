package com.example.soundattract;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import com.example.soundattract.SoundTracker;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.register(this); 

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(SoundAttractMod::onClientSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC, "soundattract-common.toml");

        if (ModList.get().isLoaded("parcool")) {
            try {
                MinecraftForge.EVENT_BUS.register(com.example.soundattract.integration.ParcoolIntegrationEvents.class);
            } catch (Exception | NoClassDefFoundError e) {
            }
        }
        MinecraftForge.EVENT_BUS.register(com.example.soundattract.integration.VanillaIntegrationEvents.class);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        SoundAttractConfig.bakeConfig(); // Ensure config cache is populated before any events
        SoundAttractNetwork.register();
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("voicechat")) {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractMod] Registering VoiceChat integration on client setup");
            }
            com.example.soundattract.SoundAttractClientEvents.registerVoiceChatIntegration();
        } else {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractMod] VoiceChat mod not present; skipping integration");
            }
        }
    }
}
