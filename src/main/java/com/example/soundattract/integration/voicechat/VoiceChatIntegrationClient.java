package com.example.soundattract.integration.voicechat;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null || !SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }


        double db = computePeakDb(rawAudio);


        double normDb = db - (-127.0);


        double factor = 0.0;
        List<VoiceChatThresholds.Threshold> pairs = VoiceChatThresholds.getThresholds(SoundAttractConfig.COMMON.voiceChatDbThresholdMap.get());
        if (!pairs.isEmpty()) {
            for (VoiceChatThresholds.Threshold p : pairs) {
                if (normDb >= p.threshold) {
                    factor = p.multiplier;
                    break;
                }
            }
        } else {

            if (normDb >= 50.0) {
                factor = 1.0;
            } else if (normDb >= 30.0) {
                factor = 0.7;
            } else if (normDb >= 10.0) {
                factor = 0.3;
            } else {
                factor = 0.0;
            }
        }


        boolean isWhispering = event.isWhispering();
        int baseRange = isWhispering
                ? SoundAttractConfig.COMMON.voiceChatWhisperRange.get()
                : SoundAttractConfig.COMMON.voiceChatNormalRange.get();

        int effectiveRange = (int) Math.round(baseRange * factor);
        if (effectiveRange <= 0) {
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[SVC Client] dbFS={} normDb={} factor={} whispering={} baseRange={} effectiveRange={}",
                db, normDb, factor, isWhispering, baseRange, effectiveRange
            );
        }

        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

        double weight = SoundAttractConfig.COMMON.voiceChatWeight.get();

        SoundMessage msg = new SoundMessage(
            SoundMessage.VOICE_CHAT_SOUND_ID,
            x, y, z,
            dim,
            sourcePlayerUUID,
            effectiveRange,
            weight
        );
        SoundAttractNetwork.INSTANCE.sendToServer(msg);
    }


    private static double computePeakDb(short[] samples) {
        int highest = 0;
        for (short s : samples) {
            int a = s == Short.MIN_VALUE ? 32768 : Math.abs(s);
            if (a > highest) highest = a;
        }
        if (highest == 0) return -127.0;
        double norm = highest / 32768.0;
        double db = 20.0 * Math.log10(norm);
        if (!Double.isFinite(db)) return -127.0;
        if (db > 0.0) db = 0.0;
        if (db < -127.0) db = -127.0;
        return db;
    }
}
