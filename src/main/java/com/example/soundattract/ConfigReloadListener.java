package com.example.soundattract;
import com.example.soundattract.config.ConfigLoader;
import com.example.soundattract.SoundAttractMod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ConfigReloadListener {
    public static void reloadConfig() {
        SoundAttractMod.CONFIG = ConfigLoader.load();


        FovEvents.buildCaches();
        DynamicScanCooldownManager.initialize();

        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[ConfigReloadListener] Reloaded config from disk and updated dependent components.");
        }
    }

    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("soundattract_reload")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ConfigReloadListener::runReloadCommand)
            );
        });
    }

    private static int runReloadCommand(CommandContext<ServerCommandSource> context) {
        reloadConfig();
        context.getSource().sendFeedback(() -> Text.literal("[Sound Attract] Config reloaded!"), true);
        return Command.SINGLE_SUCCESS;
    }
}

