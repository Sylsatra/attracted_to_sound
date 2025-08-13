package com.example.soundattract.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
                return GSON.fromJson(reader, SoundAttractConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SoundAttractConfigData config = new SoundAttractConfigData();
        save(config);
        return config;
    }

    public static void save(SoundAttractConfigData config) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
