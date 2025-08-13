package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;







public class FabricSimpleNbtSync {







    public static void sendNbtToServer(NbtCompound nbt, Logger logger) {
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            logger.info("[FabricSimpleNbtSync] Client sending NBT: {}", nbt);
        }



        NbtCompound compoundToSend = (nbt != null) ? nbt : new NbtCompound();
        SimpleNbtSyncPayload payload = new SimpleNbtSyncPayload(compoundToSend);
        
        ClientPlayNetworking.send(payload);
    }
}