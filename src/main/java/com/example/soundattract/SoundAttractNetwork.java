package com.example.soundattract;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.Identifier;

public class SoundAttractNetwork {



    public static void sendSoundMessageToServer(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass, String gunData) {
        

        SoundMessagePayload payload = new SoundMessagePayload(
            soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, gunData
        );


        ClientPlayNetworking.send(payload);
    }
}