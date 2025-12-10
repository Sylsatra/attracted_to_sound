package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;
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
                // In 1.21.11, TagParser.parseTag may have been renamed
                parsedNbt = net.minecraft.nbt.NbtUtils.snbtToStructure(nbtMatcherString);
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
        // TODO: Re-implement NBT matching for 1.21.11 when API is stable
        CompoundTag playerNbt = ((net.neoforged.neoforge.common.extensions.IEntityExtension) player)
                .getPersistentData();
        return checkNbt(playerNbt, this.nbtMatcher);
    }

    private boolean checkNbt(CompoundTag actual, CompoundTag matcher) {
        return checkNbtWithPath(actual, matcher, "");
    }

    private boolean checkNbtWithPath(CompoundTag actual, CompoundTag matcher, String path) {
        for (String key : matcher.keySet()) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            net.minecraft.nbt.Tag matcherTag = matcher.get(key);
            net.minecraft.nbt.Tag actualTag = actual.get(key);
            if (matcherTag == null || actualTag == null || matcherTag.getId() != actualTag.getId()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.debug("PlayerProfile '{}' NBT mismatch: missing key or type at '{}'", profileName, fullPath);
                }
                return false;
            }
            if (matcherTag.getId() == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                java.util.Optional<CompoundTag> actualCompoundOpt = actual.getCompound(key);
                java.util.Optional<CompoundTag> matcherCompoundOpt = matcher.getCompound(key);
                if (actualCompoundOpt.isEmpty() || matcherCompoundOpt.isEmpty()) {
                    return false;
                }
                if (!checkNbtWithPath(actualCompoundOpt.get(), matcherCompoundOpt.get(), fullPath)) {
                    return false;
                }
            } else if (matcherTag.getId() == net.minecraft.nbt.Tag.TAG_LIST) {
                if (matcherTag instanceof net.minecraft.nbt.ListTag matcherList && !matcherList.isEmpty()) {
                    if (actualTag instanceof net.minecraft.nbt.ListTag actualList) {
                        net.minecraft.nbt.Tag matchElem = matcherList.getFirst();
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
                }
            } else {
                if (!actualTag.equals(matcherTag)) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.debug("PlayerProfile '{}' NBT value mismatch at '{}': actual={} expected={}", profileName, fullPath, actualTag, matcherTag);
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
                (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.toString() : "") +
                ", detectionOverrides=" + detectionOverrides +
                '}';
    }
}
