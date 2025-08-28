package com.example.soundattract;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.example.soundattract.config.ConfigHelper;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.loot.ModLootModifiers;
import com.example.soundattract.integration.PlasmoVoiceBootstrap;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ModEnchantments.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(SoundAttractMod::onClientSetup);

                ConfigHelper.register();
        
        MinecraftForge.EVENT_BUS.register(new FovEvents());
        MinecraftForge.EVENT_BUS.register(new StealthDetectionEvents());
        MinecraftForge.EVENT_BUS.register(new PlasmoVoiceBootstrap());
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SoundAttractConfig.bakeConfig();
            SoundAttractNetwork.register();
        });
        event.enqueueWork(this::handleTaczIntegration);
    }

    private void handleTaczIntegration() {
        if (ModList.get().isLoaded("tacz") && SoundAttractConfig.TACZ_ENABLED_CACHE) {
            LOGGER.info("Tacz mod found and integration is enabled. Registering event listeners.");
            try {
                com.example.soundattract.integration.TaczIntegrationHandler.register();
            } catch (NoClassDefFoundError e) {
                LOGGER.error("Failed to register Tacz integration events. The Tacz API might be missing or has changed.", e);
            }
        } else {
            LOGGER.info("Tacz integration is disabled or mod not found.");
        }
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("voicechat")) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.info("[SoundAttractMod] Registering VoiceChat integration on client setup");
                }
                SoundAttractClientEvents.registerVoiceChatIntegration();
            } else {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.info("[SoundAttractMod] VoiceChat mod not present; skipping integration");
                }
            }
        });
    }
}