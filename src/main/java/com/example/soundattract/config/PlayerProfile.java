package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PlayerProfile {
    private final String profileName;
    @Nullable private final NbtCompound nbtMatcher;
    private final Map<PlayerStance, Double> detectionOverrides;

    public PlayerProfile(String profileName,
                         @Nullable NbtCompound nbtMatcher,
                         Map<PlayerStance, Double> detectionOverrides) {
        this.profileName = Objects.requireNonNull(profileName, "profileName");
        this.nbtMatcher = nbtMatcher;
        this.detectionOverrides = Map.copyOf(Objects.requireNonNull(detectionOverrides));
    }

    public Optional<Double> getDetectionOverride(PlayerStance stance) {
        return Optional.ofNullable(detectionOverrides.get(stance));
    }

    public boolean matches(PlayerEntity player) {
        try {
            if (player == null) return false;

            boolean isDebugging = SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging;

            if (isDebugging) {
                SoundAttractMod.LOGGER.info("[PlayerProfile] Checking profile '{}' for player '{}'", this.profileName, player.getName().getString());
            }

            if (nbtMatcher == null || nbtMatcher.isEmpty()) {
                if (isDebugging) SoundAttractMod.LOGGER.info("[PlayerProfile] SUCCESS: Profile matched (no NBT matcher).");
                return true;
            }

            NbtCompound playerNbt = new NbtCompound();
            player.writeNbt(playerNbt);

            if (isDebugging) {
                SoundAttractMod.LOGGER.info("[PlayerProfile] Player NBT to be checked: {}", playerNbt.asString());
                SoundAttractMod.LOGGER.info("[PlayerProfile] Matcher NBT to be used: {}", nbtMatcher.asString());
            }

            boolean result = checkNbt(playerNbt, nbtMatcher);

            if (isDebugging) {
                if (result) {
                    SoundAttractMod.LOGGER.info("[PlayerProfile] SUCCESS: Profile '{}' matched player '{}'.", this.profileName, player.getName().getString());
                } else {
                    SoundAttractMod.LOGGER.info("[PlayerProfile] FAILED: Profile '{}' did not match player '{}'.", this.profileName, player.getName().getString());
                }
            }
            return result;

        } catch (Throwable t) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[PlayerProfile] Match check failed for profile '{}': {}", profileName, t.getMessage());
            }
            return false;
        }
    }

    private boolean checkNbt(NbtCompound playerNbt, NbtCompound matcherNbt) {
        boolean isDebugging = SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging;

        for (String key : matcherNbt.getKeys()) {
            int matcherType = matcherNbt.getType(key);

            if (!playerNbt.contains(key, matcherType)) {
                if (isDebugging) {
                    SoundAttractMod.LOGGER.info("[PlayerProfile] Check failed: Player NBT does not contain key '{}' with matching type.", key);
                }
                return false;
            }

            if (matcherType == NbtElement.COMPOUND_TYPE) {
                if (!checkNbt(playerNbt.getCompound(key), matcherNbt.getCompound(key))) {
                    return false;
                }
            } else if (matcherType == NbtElement.LIST_TYPE) {
                NbtList matcherList = (NbtList) matcherNbt.get(key);
                NbtElement rawPlayerTag = playerNbt.get(key);

                if (!(rawPlayerTag instanceof NbtList)) {
                    if (isDebugging) {
                        SoundAttractMod.LOGGER.info("[PlayerProfile] Check failed: Player NBT for key '{}' is not a List.", key);
                    }
                    return false;
                }
                NbtList playerList = (NbtList) rawPlayerTag;

                if (!playerList.containsAll(matcherList)) {
                    if (isDebugging) {
                        SoundAttractMod.LOGGER.info("[PlayerProfile] Check failed: Player list for key '{}' does not contain all matcher elements.", key);
                        SoundAttractMod.LOGGER.info("[PlayerProfile] -> Player List: {}", playerList.asString());
                        SoundAttractMod.LOGGER.info("[PlayerProfile] -> Matcher List: {}", matcherList.asString());
                    }
                    return false;
                }
            } else {
                if (!playerNbt.get(key).equals(matcherNbt.get(key))) {
                    if (isDebugging) {
                        SoundAttractMod.LOGGER.info("[PlayerProfile] Check failed: Values for key '{}' do not match.", key);
                        SoundAttractMod.LOGGER.info("[PlayerProfile] -> Player Value: {}", playerNbt.get(key).asString());
                        SoundAttractMod.LOGGER.info("[PlayerProfile] -> Matcher Value: {}", matcherNbt.get(key).asString());
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
                (nbtMatcher != null ? ", nbtMatcher=" + nbtMatcher.asString() : "") +
                ", detectionOverrides=" + detectionOverrides +
                '}';
    }
}