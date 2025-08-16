package com.example.soundattract;

import org.slf4j.Logger;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.loot.ModLootModifiers;
import com.example.soundattract.event.AIModificationEvents;
import com.example.soundattract.integration.TaczIntegrationServerEvents;
import com.example.soundattract.integration.PlasmoVoiceBootstrap;
import com.example.soundattract.integration.VanillaIntegrationEvents;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod(IEventBus modEventBus, ModContainer container) {

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::onConfigLoad);
        ModLootModifiers.register(modEventBus);
        modEventBus.addListener(this::registerPacketHandlers);

        container.registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC);

        NeoForge.EVENT_BUS.register(new FovEvents());
        NeoForge.EVENT_BUS.register(new StealthDetectionEvents());
        NeoForge.EVENT_BUS.register(new AIModificationEvents());
        NeoForge.EVENT_BUS.register(new SoundAttractionEvents());
        NeoForge.EVENT_BUS.register(VanillaIntegrationEvents.class);
        com.example.soundattract.integration.TaczIntegration.register();


        NeoForge.EVENT_BUS.register(new PlasmoVoiceBootstrap());

        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new SoundAttractClientEvents());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SoundAttractConfig.COMMON_SPEC) {
            LOGGER.info("Baking SoundAttractMod config values due to config event.");
            SoundAttractConfig.bakeConfig();
        }
    }
    private void clientSetup(final FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("voicechat")) {
            LOGGER.info("[SoundAttractMod] VoiceChat mod is loaded. Integration is active.");
        } else {
            LOGGER.info("[SoundAttractMod] VoiceChat mod not present; skipping integration");
        }
    }
    private void registerPacketHandlers(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(SoundMessage.TYPE, SoundMessage.STREAM_CODEC, SoundMessage::handle);
    }
}