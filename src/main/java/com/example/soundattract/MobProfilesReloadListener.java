package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MobProfilesReloadListener extends SimpleJsonResourceReloadListener {

    public MobProfilesReloadListener() {
        super(new Gson(), "profiles/mobs");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<MobProfile> profiles = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : elements.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();

            boolean replace = obj.has("replace") && obj.get("replace").isJsonPrimitive()
                    && obj.get("replace").getAsJsonPrimitive().isBoolean()
                    && obj.get("replace").getAsBoolean();
            if (replace) {
                profiles.clear();
            }

            if (!obj.has("profiles") || !obj.get("profiles").isJsonArray()) {
                continue;
            }

            JsonArray arr = obj.getAsJsonArray("profiles");
            for (JsonElement profileEl : arr) {
                if (!profileEl.isJsonObject()) continue;
                JsonObject pObj = profileEl.getAsJsonObject();

                String name = pObj.has("name") ? pObj.get("name").getAsString().trim() : "";
                if (name.isEmpty()) {
                    continue;
                }

                String mobIdStr = pObj.has("mob") ? pObj.get("mob").getAsString().trim() : "*";
                if (mobIdStr.isEmpty()) mobIdStr = "*";

                String nbtStr = pObj.has("nbt") ? pObj.get("nbt").getAsString().trim() : null;
                CompoundTag matcher = null;
                if (nbtStr != null && !nbtStr.isEmpty()) {
                    try {
                        matcher = TagParser.parseTag(nbtStr);
                    } catch (Exception e) {
                        SoundAttractMod.LOGGER.warn("[MobProfilesReloadListener] Failed to parse NBT matcher for mob profile '{}': {}", name, e.getMessage());
                    }
                }

                List<SoundOverride> soundOverrides = new ArrayList<>();
                if (pObj.has("sound_overrides") && pObj.get("sound_overrides").isJsonArray()) {
                    JsonArray soArr = pObj.getAsJsonArray("sound_overrides");
                    for (JsonElement soEl : soArr) {
                        if (!soEl.isJsonObject()) continue;
                        JsonObject soObj = soEl.getAsJsonObject();
                        if (!soObj.has("sound")) continue;
                        String soundStr = soObj.get("sound").getAsString().trim();
                        ResourceLocation soundId = ResourceLocation.tryParse(soundStr);
                        if (soundId == null) continue;
                        double range = soObj.has("range") ? soObj.get("range").getAsDouble() : 32.0;
                        double weight = soObj.has("weight") ? soObj.get("weight").getAsDouble() : 1.0;
                        soundOverrides.add(new SoundOverride(soundId, range, weight));
                    }
                }

                Map<PlayerStance, Double> detectionOverrides = new EnumMap<>(PlayerStance.class);
                if (pObj.has("detection_overrides") && pObj.get("detection_overrides").isJsonObject()) {
                    JsonObject detObj = pObj.getAsJsonObject("detection_overrides");
                    for (PlayerStance stance : PlayerStance.values()) {
                        String key = stance.getConfigName();
                        if (detObj.has(key)) {
                            try {
                                double val = detObj.get(key).getAsDouble();
                                detectionOverrides.put(stance, val);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                String nbtMatcherStr = matcher != null ? matcher.toString() : null;
                MobProfile profile = new MobProfile(name, mobIdStr, nbtMatcherStr, soundOverrides, detectionOverrides);
                profiles.add(profile);
            }
        }

        SoundAttractConfig.DP_MOB_PROFILES_CACHE = profiles;

        try {
            SoundAttractConfig.bakeConfig();
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[MobProfilesReloadListener] Failed to re-bake config after datapack reload", t);
        }
    }
}
