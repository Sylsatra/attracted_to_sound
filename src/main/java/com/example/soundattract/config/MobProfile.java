package com.example.soundattract.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.BuiltInRegistries;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class MobProfile {
    private final String profileName;
    private final String mobIdString;
    @Nullable
    private final Identifier mobId;
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
            this.mobId = Identifier.tryParse(mobIdString);
            if (this.mobId == null) {
                SoundAttractMod.LOGGER.warn("Invalid mobIdString for profile '{}': {}. This profile will be disabled.", profileName, mobIdString);
                profileIsValid = false;
            }
        }
        this.isValid = profileIsValid;

        CompoundTag parsedNbt = null;
        if (nbtMatcherString != null && !nbtMatcherString.trim().isEmpty()) {
            try {
                parsedNbt = net.minecraft.nbt.NbtUtils.snbtToStructure(nbtMatcherString);
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
    public Identifier getMobId() {
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

    public Optional<SoundOverride> getSoundOverride(Identifier soundId) {
        return soundOverrides.stream()
                .filter(override -> override.soundId().equals(soundId))
                .findFirst();
    }

    public static MobProfile fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] parts = raw.split(";", 5);
        if (parts.length < 2) {
            SoundAttractMod.LOGGER.warn("Invalid mob profile string: '{}'. Must have at least profileName and mobId.", raw);
            return null;
        }

        String profileName = parts[0].trim();
        String mobIdString = parts[1].trim();
        String nbtMatcherString = parts.length > 2 ? parts[2].trim() : "";
        String soundOverridesString = parts.length > 3 ? parts[3].trim() : "";
        String detectionOverridesString = parts.length > 4 ? parts[4].trim() : "";

        List<SoundOverride> soundOverrides = parseSoundOverrides(soundOverridesString, profileName);
        Map<PlayerStance, Double> detectionOverrides = parseDetectionOverrides(detectionOverridesString, profileName);

        return new MobProfile(profileName, mobIdString, nbtMatcherString, soundOverrides, detectionOverrides);
    }

    public record SoundOverride(Identifier soundId, double range, double weight) {
        public static SoundOverride fromString(String raw, String profileName) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String[] parts = raw.split(":");
            if (parts.length < 4) {
                SoundAttractMod.LOGGER.warn("Invalid sound override format in profile '{}': '{}'. Expected 'namespace:soundPath:range:weight'", profileName, raw);
                return null;
            }
            String soundIdStr = parts[0].trim() + ":" + parts[1].trim();
            Identifier soundId = Identifier.tryParse(soundIdStr);
            if (soundId == null) {
                SoundAttractMod.LOGGER.warn("Invalid sound ID in profile '{}': '{}'", profileName, soundIdStr);
                return null;
            }
            try {
                double range = Double.parseDouble(parts[2].trim());
                double weight = Double.parseDouble(parts[3].trim());
                return new SoundOverride(soundId, range, weight);
            } catch (NumberFormatException e) {
                SoundAttractMod.LOGGER.warn("Invalid range or weight in profile '{}': '{}'", profileName, raw, e);
                return null;
            }
        }
    }

    private static List<SoundOverride> parseSoundOverrides(String soundOverridesString, String profileName) {
        if (soundOverridesString == null || soundOverridesString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(soundOverridesString.split(","))
                .map(s -> SoundOverride.fromString(s.trim(), profileName))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<PlayerStance, Double> parseDetectionOverrides(String detectionOverridesString, String profileName) {
        if (detectionOverridesString.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<PlayerStance, Double> overrides = new HashMap<>();
        for (String part : detectionOverridesString.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length == 2) {
                String stanceStr = pair[0].trim().toUpperCase();
                String valueStr = pair[1].trim();
                PlayerStance stance = null;
                for (PlayerStance s : PlayerStance.values()) {
                    if (s.name().equals(stanceStr)) {
                        stance = s;
                        break;
                    }
                }

                if (stance == null) {
                    SoundAttractMod.LOGGER.warn("Invalid player stance '{}' in profile '{}'", pair[0], profileName);
                    continue;
                }

                try {
                    double value = Double.parseDouble(valueStr);
                    overrides.put(stance, value);
                } catch (NumberFormatException e) {
                    SoundAttractMod.LOGGER.warn("Invalid detection override value '{}' for stance '{}' in profile '{}'", valueStr, stanceStr, profileName);
                }
            }
        }
        return overrides;
    }

    public boolean matches(Mob mob) {
        if (!this.isValid) {
            return false;
        }

        if (this.mobId == null) {
            if (this.nbtMatcher != null && !this.nbtMatcher.isEmpty()) {
                CompoundTag mobNbt = ((net.neoforged.neoforge.common.extensions.IEntityExtension) mob)
                        .getPersistentData();
                return checkNbt(mobNbt, this.nbtMatcher);
            }
            return true;
        }

        Identifier actualMobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!this.mobId.equals(actualMobId)) {
            return false;
        }

        if (this.nbtMatcher != null && !this.nbtMatcher.isEmpty()) {
            // TODO: Re-implement NBT matching for 1.21.11 when API is stable
            CompoundTag mobNbt = ((net.neoforged.neoforge.common.extensions.IEntityExtension) mob)
                    .getPersistentData();
            if (!checkNbt(mobNbt, this.nbtMatcher)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean checkNbt(CompoundTag mobNbt, CompoundTag matcherNbt) {
        for (String key : matcherNbt.keySet()) {
            if (!mobNbt.contains(key)) {
                return false;
            }
            net.minecraft.nbt.Tag matcherTag = matcherNbt.get(key);
            net.minecraft.nbt.Tag mobTag = mobNbt.get(key);
            if (matcherTag == null || mobTag == null) {
                return false;
            }
            if (matcherTag.getId() != mobTag.getId()) {
                return false;
            }
            if (matcherTag.getId() == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                java.util.Optional<CompoundTag> mobCompoundOpt = mobNbt.getCompound(key);
                java.util.Optional<CompoundTag> matcherCompoundOpt = matcherNbt.getCompound(key);
                if (mobCompoundOpt.isEmpty() || matcherCompoundOpt.isEmpty()) {
                    return false;
                }
                if (!checkNbt(mobCompoundOpt.get(), matcherCompoundOpt.get())) {
                    return false;
                }
            } else if (matcherTag.getId() == net.minecraft.nbt.Tag.TAG_LIST) {
                if (!mobTag.equals(matcherTag)) {
                    SoundAttractMod.LOGGER.trace("NBT list matching for key '{}' is currently basic. Profile: {}", key, profileName);
                }
            } else {
                if (!mobTag.equals(matcherTag)) {
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
                (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.toString() : "") +
                ", soundOverrides=" + soundOverrides +
                ", detectionOverrides=" + detectionOverrides +
                '}';
    }
}
