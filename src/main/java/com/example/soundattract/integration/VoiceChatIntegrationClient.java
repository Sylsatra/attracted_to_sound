package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.SoundMessagePayload;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ClientWorld world = MinecraftClient.getInstance().world;
            SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] handleClientSound called: isWhispering={}, playerPos=({}, {}, {}), dim={}, uuid={}",
                    event.isWhispering(),
                    player != null ? player.getX() : -1,
                    player != null ? player.getY() : -1,
                    player != null ? player.getZ() : -1,
                    world != null ? world.getRegistryKey().getValue() : "null",
                    player != null ? player.getUuid() : "null");
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity clientPlayer = mc.player;
        ClientWorld clientWorld = mc.world;

        if (clientPlayer == null || clientWorld == null) {
            SoundAttractMod.LOGGER.warn("[VoiceChatIntegrationClient] Client world or player is null in handleClientSound, skipping event.");
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }

        double db = computePeakDb(rawAudio);


        double normDb = db - (-127.0);


        double factor = 0.0;
        List<? extends String> rawMap = SoundAttractMod.CONFIG.voiceChatDbThresholdMap;
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
        int baseRange = isWhispering ? SoundAttractMod.CONFIG.voiceChatWhisperRange : SoundAttractMod.CONFIG.voiceChatNormalRange;
        double weight = SoundAttractMod.CONFIG.voiceChatWeight;
        int effectiveRange = (int) Math.round(baseRange * factor);
        if (effectiveRange <= 0) {
            return;
        }
        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        Identifier dim = clientWorld.getRegistryKey().getValue();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUuid());


        SoundAttractNetwork.sendSoundMessageToServer(
                SoundMessagePayload.VOICE_CHAT_SOUND_ID,
                x,
                y,
                z,
                dim,
                sourcePlayerUUID,
                effectiveRange,
                weight,
                null,
                null
        );
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