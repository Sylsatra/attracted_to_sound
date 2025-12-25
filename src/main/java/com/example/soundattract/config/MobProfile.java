package com.example.soundattract.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MobProfile {
    private final String profileName;
    private final String mobIdString;
    @Nullable
    private final ResourceLocation mobId;
    @Nullable
    private final CompoundTag nbtMatcher;
    private final List<SoundOverride> soundOverrides;
    private final Map<PlayerStance, Double> detectionOverrides;
    private final boolean isValid;

    public MobProfile(String profileName, String mobIdString, @Nullable String nbtMatcherString,
                      List<SoundOverride> soundOverrides, Map<PlayerStance, Double> detectionOverrides) {
        this.profileName = Objects.requireNonNull(profileName, "profileName cannot be null");
        this.mobIdString = Objects.requireNonNull(mobIdString, "mobIdString cannot be null");
        boolean profileIsValid = true;
        if ("*".equals(mobIdString)) {
            this.mobId = null;
        } else {
            this.mobId = ResourceLocation.tryParse(mobIdString);
            if (this.mobId == null) {
                SoundAttractMod.LOGGER.warn("Invalid mobIdString for profile '{}': {}. This profile will be disabled.", profileName, mobIdString);
                profileIsValid = false;
            }
        }
        this.isValid = profileIsValid;

        CompoundTag parsedNbt = null;
        if (nbtMatcherString != null && !nbtMatcherString.trim().isEmpty()) {
            try {
                parsedNbt = TagParser.parseTag(nbtMatcherString);
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse NBT matcher for profile '{}': {}. Error: {}", profileName, nbtMatcherString, e.getMessage());
            }
        }
        this.nbtMatcher = parsedNbt;
        this.soundOverrides = Collections.unmodifiableList(Objects.requireNonNull(soundOverrides, "soundOverrides cannot be null"));
        this.detectionOverrides = Collections.unmodifiableMap(Objects.requireNonNull(detectionOverrides, "detectionOverrides cannot be null"));
    }

    public String getProfileName() {
        return profileName;
    }

    public String getMobIdString() {
        return mobIdString;
    }

    @Nullable
    public ResourceLocation getMobId() {
        return mobId;
    }

    @Nullable
    public CompoundTag getNbtMatcher() {
        return nbtMatcher;
    }

    public List<SoundOverride> getSoundOverrides() {
        return soundOverrides;
    }

    public Map<PlayerStance, Double> getDetectionOverrides() {
        return detectionOverrides;
    }

    public Optional<Double> getDetectionOverride(PlayerStance stance) {
        return Optional.ofNullable(detectionOverrides.get(stance));
    }

    public Optional<SoundOverride> getSoundOverride(ResourceLocation soundId) {
        return soundOverrides.stream()
                .filter(override -> override.getSoundId().equals(soundId))
                .findFirst();
    }

    public boolean matches(Mob mob) {
        if (!this.isValid) {
            return false;
        }

        if (this.mobId == null) {
            if (this.nbtMatcher != null && !this.nbtMatcher.isEmpty()) {
                CompoundTag mobNbt = mob.saveWithoutId(new CompoundTag());
                return checkNbt(mobNbt, this.nbtMatcher);
            }
            return true;
        }

        ResourceLocation actualMobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (!this.mobId.equals(actualMobId)) {
            return false;
        }

        if (this.nbtMatcher != null && !this.nbtMatcher.isEmpty()) {
            CompoundTag mobNbt = mob.saveWithoutId(new CompoundTag());
            if (!checkNbt(mobNbt, this.nbtMatcher)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean checkNbt(CompoundTag mobNbt, CompoundTag matcherNbt) {
        for (String key : matcherNbt.getAllKeys()) {
            if (!mobNbt.contains(key, matcherNbt.getTagType(key))) {
                return false;
            }
            if (matcherNbt.getTagType(key) == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                if (!checkNbt(mobNbt.getCompound(key), matcherNbt.getCompound(key))) {
                    return false;
                }
            } else if (matcherNbt.getTagType(key) == net.minecraft.nbt.Tag.TAG_LIST) {
                if (!mobNbt.getList(key, matcherNbt.getList(key, 0).getElementType()).equals(matcherNbt.getList(key, 0))) {
                    SoundAttractMod.LOGGER.trace("NBT list matching for key '{}' is currently basic. Profile: {}", key, profileName);
                }
            } else {
                if (!mobNbt.get(key).equals(matcherNbt.get(key))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MobProfile{" +
                "profileName='" + profileName + '\'' +
                ", mobIdString='" + mobIdString + '\'' +
                (mobId != null ? ", mobId=" + mobId : "") +
                (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.getAsString() : "") +
                ", soundOverrides=" + soundOverrides +
                ", detectionOverrides=" + detectionOverrides +
                '}';
    }
}
