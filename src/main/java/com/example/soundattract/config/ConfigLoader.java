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
