package com.example.soundattract.config;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.nbt.StringNbtReader;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MobProfile {
    private final String profileName;
    private final String mobIdString;
    @Nullable
    private final Identifier mobId;
    @Nullable
    private final NbtCompound nbtMatcher;
    private final List<SoundOverride> soundOverrides;
    private final Map<PlayerStance, Double> detectionOverrides;

    public MobProfile(String profileName,
                      String mobIdString,
                      @Nullable String nbtMatcherString,
                      List<SoundOverride> soundOverrides,
                      Map<PlayerStance, Double> detectionOverrides) {
        this.profileName = Objects.requireNonNull(profileName, "profileName cannot be null");
        this.mobIdString = Objects.requireNonNull(mobIdString, "mobIdString cannot be null");

        if ("*".equals(mobIdString)) {
            this.mobId = null;
        } else {
            this.mobId = Identifier.tryParse(mobIdString);
            if (this.mobId == null) {
                SoundAttractMod.LOGGER.warn(
                    "Invalid mobIdString for profile '{}': {}. Will not match specific mob type.",
                    profileName, mobIdString
                );
            }
        }

        NbtCompound parsedNbt = null;
        if (nbtMatcherString != null && !nbtMatcherString.trim().isEmpty()) {
            try {
                parsedNbt = StringNbtReader.parse(nbtMatcherString);
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn(
                    "Failed to parse NBT matcher for profile '{}': {}. Error: {}",
                    profileName, nbtMatcherString, e.getMessage()
                );
            }
        }
        this.nbtMatcher = parsedNbt;

        this.soundOverrides = Collections.unmodifiableList(
            Objects.requireNonNull(soundOverrides, "soundOverrides cannot be null")
        );
        this.detectionOverrides = Collections.unmodifiableMap(
            Objects.requireNonNull(detectionOverrides, "detectionOverrides cannot be null")
        );
    }

    public String getProfileName() {
        return profileName;
    }

    public String getMobIdString() {
        return mobIdString;
    }

    @Nullable
    public Identifier getMobId() {
        return mobId;
    }

    @Nullable
    public NbtCompound getNbtMatcher() {
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

    public Optional<SoundOverride> getSoundOverride(Identifier soundId) {
        return soundOverrides.stream()
                             .filter(override -> override.getSoundId().equals(soundId))
                             .findFirst();
    }

    public boolean matches(MobEntity mob) {
        // 1) If a specific mobId was provided, check the registry key
        if (this.mobId != null) {
            Optional<RegistryKey<EntityType<?>>> keyOpt = Registries.ENTITY_TYPE.getKey(mob.getType());
            if (keyOpt.isEmpty() || !this.mobId.equals(keyOpt.get().getValue())) {
                return false;
            }
        }

        // 2) If there's an NBT matcher, compare recursively
        if (this.nbtMatcher != null && !this.nbtMatcher.isEmpty()) {
            // saveWithoutId now requires a boolean for dynamic data; 'true' includes entity data
            NbtCompound mobNbt = new NbtCompound();
            mob.writeNbt(mobNbt);
            if (!checkNbt(mobNbt, this.nbtMatcher)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkNbt(NbtCompound mobNbt, NbtCompound matcherNbt) {
        for (String key : matcherNbt.getKeys()) {
            int matcherType = matcherNbt.getType(key);
            // 1) If the mob doesn't contain this key or the tag type differs, fail
            if (!mobNbt.contains(key, matcherType)) {
                return false;
            }
            // 2) If it's a nested compound, recurse
            if (matcherType == NbtElement.COMPOUND_TYPE) {
                if (!checkNbt(mobNbt.getCompound(key), matcherNbt.getCompound(key))) {
                    return false;
                }
            }
            // 3) If it's a list, compare entire lists
            else if (matcherType == NbtElement.LIST_TYPE) {
                // Grab the matcher list by casting the raw element
                NbtList matcherList = (NbtList) matcherNbt.get(key);
                byte elementType = matcherList.getType();
                // Now fetch the corresponding list from the mob NBT
                NbtList mobList = mobNbt.getList(key, elementType);
                if (!mobList.equals(matcherList)) {
                    SoundAttractMod.LOGGER.trace(
                        "NBT list matching for key '{}' is currently basic. Profile: {}",
                        key, profileName
                    );
                }
            }
            // 4) Otherwise, compare the values directly
            else {
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
               (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.asString() : "") +
               ", soundOverrides=" + soundOverrides +
               ", detectionOverrides=" + detectionOverrides +
               '}';
    }
}
