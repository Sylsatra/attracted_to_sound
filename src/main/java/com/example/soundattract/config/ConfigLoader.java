package com.example.soundattract.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/soundattract.json";

    public static SoundAttractConfigData load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {

                JsonElement userJson = JsonParser.parseReader(reader);


                JsonObject defaultsJson = GSON.toJsonTree(new SoundAttractConfigData()).getAsJsonObject();


                if (userJson != null && userJson.isJsonObject()) {
                    JsonObject userObj = userJson.getAsJsonObject();
                    migrateUserJson(userObj);
                    JsonObject merged = deepMergeObjects(userObj, defaultsJson);
                    SoundAttractConfigData mergedConfig = GSON.fromJson(merged, SoundAttractConfigData.class);

                    save(mergedConfig);
                    return mergedConfig;
                } else {

                    SoundAttractConfigData cfg = new SoundAttractConfigData();
                    save(cfg);
                    return cfg;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SoundAttractConfigData config = new SoundAttractConfigData();
        save(config);
        return config;
    }

    public static void save(SoundAttractConfigData config) {
        try (FileWriter writer = new FileWriter(ensureConfigPath())) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String ensureConfigPath() throws IOException {
        File file = new File(CONFIG_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IOException("Failed to create config directory: " + parent.getAbsolutePath());
            }
        }
        return file.getPath();
    }

    /**
     * Perform JSON-level migrations from legacy keys to current schema before merging with defaults.
     * Keep this deterministic and side-effect free besides editing the JSON object in place.
     */
    private static void migrateUserJson(JsonObject obj) {
        int version = obj.has("configSchemaVersion") && obj.get("configSchemaVersion").isJsonPrimitive()
                ? safeGetInt(obj.get("configSchemaVersion"), 0)
                : 0;


        if (version < 1) {

            renameKey(obj, "attractedEntityIds", "attractedEntities");


            renameKey(obj, "scanCooldown", "scanCooldownTicks");


            renameKey(obj, "nonPlayerSounds", "nonPlayerSoundIdList");


            renameKey(obj, "nonPlayerSoundWhitelist", "soundIdWhitelist");


            renameKey(obj, "edgeMobSmart", "edgeMobSmartBehavior");


            renameKey(obj, "pointblankGunShootRanges", "pointblankGunShootRanges");
            renameKey(obj, "pointblankAttachmentReductions", "pointblankAttachmentSoundReductions");
            renameKey(obj, "pointblankMuzzleFlashReductionsMap", "pointblankMuzzleFlashReductions");


            obj.addProperty("configSchemaVersion", 1);
        }

        if (version < 4) {
            if (!obj.has("enableFleeFromUnseenAttackerGoal")) {
                obj.addProperty("enableFleeFromUnseenAttackerGoal", true);
            }
            obj.addProperty("configSchemaVersion", 4);
        }

        if (version < 5) {
            if (!obj.has("enableTeleportToSound")) {
                obj.addProperty("enableTeleportToSound", true);
            }
            if (!obj.has("teleportChance")) {
                obj.addProperty("teleportChance", 0.35);
            }
            if (!obj.has("teleportCooldownTicks")) {
                obj.addProperty("teleportCooldownTicks", 300);
            }
            if (!obj.has("teleportCanTeleportTag")) {
                obj.addProperty("teleportCanTeleportTag", "enhancedai:mobs/teleport_to_target/can_teleport");
            }
            if (!obj.has("teleportCanBeTeleportedTag")) {
                obj.addProperty("teleportCanBeTeleportedTag", "enhancedai:mobs/teleport_to_target/can_be_teleported");
            }

            if (!obj.has("enablePickUpAndThrowToSound")) {
                obj.addProperty("enablePickUpAndThrowToSound", true);
            }
            if (!obj.has("pickUpChance")) {
                obj.addProperty("pickUpChance", 0.05);
            }
            if (!obj.has("pickUpCooldownTicks")) {
                obj.addProperty("pickUpCooldownTicks", 600);
            }
            if (!obj.has("pickUpMinDistanceToPickUp")) {
                obj.addProperty("pickUpMinDistanceToPickUp", 5);
            }
            if (!obj.has("pickUpMaxDistanceToThrow")) {
                obj.addProperty("pickUpMaxDistanceToThrow", 24);
            }
            if (!obj.has("pickUpSpeedModifier")) {
                obj.addProperty("pickUpSpeedModifier", 1.25);
            }
            if (!obj.has("pickUpCanPickUpTag")) {
                obj.addProperty("pickUpCanPickUpTag", "enhancedai:mobs/pick_up_and_throw/can_pick_up");
            }
            if (!obj.has("pickUpCanBePickedUpTag")) {
                obj.addProperty("pickUpCanBePickedUpTag", "enhancedai:mobs/pick_up_and_throw/can_be_picked_up");
            }

            if (!obj.has("enableXrayTargeting")) {
                obj.addProperty("enableXrayTargeting", true);
            }
            if (!obj.has("xrayApplyTag")) {
                obj.addProperty("xrayApplyTag", "enhancedai:mobs/targeting/apply_xray");
            }
            if (!obj.has("xrayRequireBetterNearby")) {
                obj.addProperty("xrayRequireBetterNearby", true);
            }
            if (!obj.has("xrayBetterNearbyTag")) {
                obj.addProperty("xrayBetterNearbyTag", "enhancedai:mobs/targeting/better_nearby_targeting");
            }
            if (!obj.has("xrayMinRange")) {
                obj.addProperty("xrayMinRange", 16);
            }
            if (!obj.has("xrayMaxRange")) {
                obj.addProperty("xrayMaxRange", 24);
            }
            if (!obj.has("xrayChance")) {
                obj.addProperty("xrayChance", 0.5);
            }

            obj.addProperty("configSchemaVersion", 5);
        }
    }

    private static void renameKey(JsonObject obj, String oldKey, String newKey) {
        if (!oldKey.equals(newKey) && obj.has(oldKey) && !obj.has(newKey)) {
            obj.add(newKey, obj.get(oldKey));
            obj.remove(oldKey);
        }
    }

    private static int safeGetInt(JsonElement el, int fallback) {
        try {
            if (el != null && el.isJsonPrimitive()) {
                return el.getAsInt();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    /**
     * Deep-merge user values into defaults. For each key in user:
     * - if both user and defaults are JSON objects, merge recursively
     * - else, user value replaces defaults
     * Returns the mutated defaults object for convenience.
     */
    private static JsonObject deepMergeObjects(JsonObject user, JsonObject defaults) {
        for (String key : user.keySet()) {
            JsonElement userVal = user.get(key);
            if (!defaults.has(key)) {
                defaults.add(key, userVal);
                continue;
            }

            JsonElement defVal = defaults.get(key);
            if (userVal != null && userVal.isJsonObject() && defVal != null && defVal.isJsonObject()) {
                deepMergeObjects(userVal.getAsJsonObject(), defVal.getAsJsonObject());
            } else {

                defaults.add(key, userVal);
            }
        }
        return defaults;
    }
}
