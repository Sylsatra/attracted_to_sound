package com.example.soundattract.integration;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
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
        List<? extends String> rawMap = SoundAttractConfig.COMMON.voiceChatDbThresholdMap.get();
        if (rawMap != null && !rawMap.isEmpty()) {
            List<double[]> pairs = new ArrayList<>();
            for (String entry : rawMap) {
                if (entry == null || entry.isEmpty()) continue;
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    double th = Double.parseDouble(parts[0].trim());
                    double mul = Double.parseDouble(parts[1].trim());
                    pairs.add(new double[]{th, mul});
                } catch (Exception ignored) {
                }
            }

            Collections.sort(pairs, new Comparator<double[]>() {
                @Override
                public int compare(double[] a, double[] b) {
                    return Double.compare(b[0], a[0]);
                }
            });
            for (double[] p : pairs) {
                if (normDb >= p[0]) {
                    factor = p[1];
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
        short highest = 0;
        for (short s : samples) {
            int a = Math.abs(s);
            if (a > highest) highest = (short) a;
        }
        if (highest == 0) return -127.0;
        double norm = Math.abs(highest) / 32768.0;
        double db = 20.0 * Math.log10(norm);
        if (!Double.isFinite(db)) return -127.0;
        if (db > 0.0) db = 0.0;
        if (db < -127.0) db = -127.0;
        return db;
    }
}