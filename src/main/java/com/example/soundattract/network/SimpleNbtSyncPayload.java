package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;


public record SimpleNbtSyncPayload(NbtCompound nbt) implements CustomPayload {


    public static final CustomPayload.Id<SimpleNbtSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(SoundAttractMod.MOD_ID, "simple_nbt_sync"));
    

    public static final PacketCodec<RegistryByteBuf, SimpleNbtSyncPayload> CODEC = PacketCodec.of(
            SimpleNbtSyncPayload::write,
            SimpleNbtSyncPayload::new
    );


    private SimpleNbtSyncPayload(RegistryByteBuf buf) {


        this(Objects.requireNonNullElseGet(buf.readNbt(), NbtCompound::new));
    }


    private void write(RegistryByteBuf buf) {
        buf.writeNbt(this.nbt);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}