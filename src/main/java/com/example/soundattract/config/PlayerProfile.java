package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PlayerProfile {
    private final String profileName;
    @Nullable
    private final CompoundTag nbtMatcher;
    private final Map<PlayerStance, Double> detectionOverrides;

    public PlayerProfile(String profileName, @Nullable String nbtMatcherString, Map<PlayerStance, Double> detectionOverrides) {
        this.profileName = Objects.requireNonNull(profileName, "profileName cannot be null");
        CompoundTag parsedNbt = null;
        if (nbtMatcherString != null && !nbtMatcherString.trim().isEmpty()) {
            try {
                parsedNbt = TagParser.parseTag(nbtMatcherString);
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse NBT matcher for player profile '{}': {}", profileName, e.getMessage());
            }
        }
        this.nbtMatcher = parsedNbt;
        this.detectionOverrides = Objects.requireNonNull(detectionOverrides, "detectionOverrides cannot be null");
    }

    public String getProfileName() {
        return profileName;
    }

    public Optional<Double> getDetectionOverride(PlayerStance stance) {
        return Optional.ofNullable(detectionOverrides.get(stance));
    }

    public boolean matches(Player player) {
        if (player == null) {
            return false;
        }
        if (this.nbtMatcher == null || this.nbtMatcher.isEmpty()) {
            return true;
        }
        CompoundTag playerNbt = player.saveWithoutId(new CompoundTag());
        return checkNbt(playerNbt, this.nbtMatcher);
    }

    private boolean checkNbt(CompoundTag actual, CompoundTag matcher) {
        return checkNbtWithPath(actual, matcher, "");
    }

    private boolean checkNbtWithPath(CompoundTag actual, CompoundTag matcher, String path) {
        for (String key : matcher.getAllKeys()) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (!actual.contains(key, matcher.getTagType(key))) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.debug("PlayerProfile '{}' NBT mismatch: missing key or type at '{}'", profileName, fullPath);
                }
                return false;
            }
            if (matcher.getTagType(key) == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                if (!checkNbtWithPath(actual.getCompound(key), matcher.getCompound(key), fullPath)) {
                    return false;
                }
            } else if (matcher.getTagType(key) == net.minecraft.nbt.Tag.TAG_LIST) {
                net.minecraft.nbt.ListTag matcherList = matcher.getList(key, 0);
                if (!matcherList.isEmpty()) {
                    net.minecraft.nbt.Tag matchElem = matcherList.get(0);
                    net.minecraft.nbt.ListTag actualList = actual.getList(key, matchElem.getId());
                    boolean found = false;
                    for (int i = 0; i < actualList.size(); i++) {
                        if (actualList.get(i).equals(matchElem)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found && SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.debug("PlayerProfile '{}' NBT list did not contain matcher element at '{}'", profileName, fullPath);
                    }
                }
            } else {
                if (!actual.get(key).equals(matcher.get(key))) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.debug("PlayerProfile '{}' NBT value mismatch at '{}': actual={} expected={}", profileName, fullPath, actual.get(key), matcher.get(key));
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "PlayerProfile{" +
                "profileName='" + profileName + '\'' +
                (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.getAsString() : "") +
                ", detectionOverrides=" + detectionOverrides +
                '}';
    }
}
