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
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.enchantment.ModEnchantments;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ModEnchantments.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(SoundAttractMod::onClientSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC, "soundattract-common.toml");
        
        MinecraftForge.EVENT_BUS.register(new FovEvents());
        MinecraftForge.EVENT_BUS.register(new StealthDetectionEvents());
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SoundAttractConfig.bakeConfig();
            SoundAttractNetwork.register();
        });
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