package com.example.soundattract;

import java.util.Optional;
import java.util.UUID;

import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public record SoundMessage(
    ResourceLocation soundId,
    Vec3 position,
    ResourceLocation dimension,
    Optional<UUID> sourcePlayerUUID,
    int range,
    double weight,
    Optional<String> animatorClass,
    Optional<String> taczType
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SoundMessage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "sound_message"));
    public static final ResourceLocation VOICE_CHAT_SOUND_ID = ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "voice_chat");

    private record Part1(
        ResourceLocation soundId,
        Vec3 position,
        ResourceLocation dimension,
        Optional<UUID> sourcePlayerUUID
    ) {}

    private static final StreamCodec<FriendlyByteBuf, Part1> PART1_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, Part1::soundId,
        StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new
        ), Part1::position,
        ResourceLocation.STREAM_CODEC, Part1::dimension,
        net.minecraft.core.UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), Part1::sourcePlayerUUID,
        Part1::new
    );

    public static final StreamCodec<FriendlyByteBuf, SoundMessage> STREAM_CODEC = StreamCodec.composite(
        PART1_CODEC,
        sm -> new Part1(sm.soundId(), sm.position(), sm.dimension(), sm.sourcePlayerUUID()),
        ByteBufCodecs.VAR_INT,
        SoundMessage::range,
        ByteBufCodecs.DOUBLE,
        SoundMessage::weight,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
        SoundMessage::animatorClass,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
        SoundMessage::taczType,
        (part1, range, weight, animClass, taczType) -> new SoundMessage(
            part1.soundId(),
            part1.position(),
            part1.dimension(),
            part1.sourcePlayerUUID(),
            range,
            weight,
            animClass,
            taczType
        )
    );

    public static void handle(SoundMessage msg, IPayloadContext context) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Server received a message: {}", msg);
        context.enqueueWork(() -> {
            try {
                ResourceLocation loc = msg.soundId();
                if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !loc.equals(VOICE_CHAT_SOUND_ID) && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc)) {
                    return;
                }
                ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
                ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, msg.dimension());
                ServerLevel serverLevel = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
                if (serverLevel == null) {
                    SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel is null for {}", msg.dimension());
                    return;
                }
                final Vec3 soundLocation = msg.position().equals(Vec3.ZERO) && sender != null ? sender.position() : msg.position();
                final BlockPos pos = BlockPos.containing(soundLocation);
                final String dimString = msg.dimension().toString();
                final int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
                if (msg.soundId().equals(VOICE_CHAT_SOUND_ID)) {
                    if (msg.range() > 0 && msg.animatorClass().isPresent()) {
                        SoundTracker.addVirtualSound(pos, dimString, (double) msg.range(), msg.weight(), lifetime, msg.sourcePlayerUUID().orElse(null), msg.animatorClass().get());
                    }
                } else {
                    Optional<SoundEvent> se = BuiltInRegistries.SOUND_EVENT.getOptional(msg.soundId());
                    double range = msg.range();
                    double weight = msg.weight();
                    if (range < 0) {
                         SoundAttractConfig.SoundDefaultEntry def = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(msg.soundId());
                         if (def != null) {
                            range = def.range();
                            weight = def.weight();
                        } else {
                            range = 10;
                            weight = 1.0;
                        }
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] Using fallback range/weight for {}: range={}, weight={}", msg.soundId(), range, weight);
                        }
                    }
                    final double finalRange = range;
                    final double finalWeight = weight;
                    se.ifPresent(soundEvent -> SoundTracker.addSound(soundEvent, pos, dimString, finalRange, finalWeight, lifetime, null));
                }
            } catch  (Exception e) {
                SoundAttractMod.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId(), e);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}