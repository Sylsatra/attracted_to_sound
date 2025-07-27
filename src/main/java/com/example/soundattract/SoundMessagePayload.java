package com.example.soundattract;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public record SoundMessagePayload(
    Identifier soundId,
    double x,
    double y,
    double z,
    Identifier dimension,
    Optional<UUID> sourcePlayerUUID,
    int range,
    double weight,
    String animatorClass,
    String gunData
) implements CustomPayload {

    public static final CustomPayload.Id<SoundMessagePayload> ID = new CustomPayload.Id<>(Identifier.of(SoundAttractMod.MOD_ID, "sound_message"));
    
    public static final PacketCodec<RegistryByteBuf, SoundMessagePayload> CODEC = PacketCodec.of(SoundMessagePayload::write, SoundMessagePayload::new);
    
    public static final Identifier VOICE_CHAT_SOUND_ID = Identifier.of(SoundAttractMod.MOD_ID, "voice_chat");

    public static SoundMessagePayload createPlayerSound(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID) {
        return new SoundMessagePayload(soundId, x, y, z, dimension, sourcePlayerUUID, -1, 1.0, null, null);
    }

    private SoundMessagePayload(RegistryByteBuf buf) {
        this(
            buf.readIdentifier(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readIdentifier(),

            buf.readOptional(b -> b.readUuid()),
            buf.readInt(),
            buf.readDouble(),
            buf.readNullable(PacketByteBuf::readString),
            buf.readNullable(PacketByteBuf::readString)
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(this.soundId);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeIdentifier(this.dimension);

        buf.writeOptional(this.sourcePlayerUUID, (b, uuid) -> b.writeUuid(uuid));
        buf.writeInt(this.range);
        buf.writeDouble(this.weight);
        buf.writeNullable(this.animatorClass, PacketByteBuf::writeString);
        buf.writeNullable(this.gunData, PacketByteBuf::writeString);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}