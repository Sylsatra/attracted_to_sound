package com.example.soundattract.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.*;

public class SoundAttractConfig {

    private static final String CONFIG_FILE = "soundattractmod.toml";

    public static List<String> ATTRACTED_ENTITIES = new ArrayList<>();
    public static Set<SoundEvent> SOUNDS_TO_ATTRACT = new HashSet<>();

    public static int SOUND_HEARING_RADIUS = 16;
    public static int SOUND_LIFETIME_TICKS = 60;
    public static int SCAN_COOLDOWN_TICKS = 20;
    public static double ARRIVAL_DISTANCE = 2.0;

    public static void loadConfig() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        CommentedFileConfig data = CommentedFileConfig.builder(path).autosave().build();
        data.load();

        data.setComment("soundattract",
            "Sound Attract Mod Configuration\n" +
            "This mod allows specific entities to be attracted to certain sounds.\n" +
            "Modify this config to control which mobs react and what sounds they respond to.\n" +
            "For valid sound events, check: https://minecraft.fandom.com/wiki/Sounds.json#Java_Edition_values"
        );

        if (!data.contains("soundattract.entities")) {
            data.set("soundattract.entities", Arrays.asList("minecraft:zombie", "minecraft:husk"));
        }
        data.setComment("soundattract.entities",
            "List of entity IDs that should be attracted to certain sounds.\n" +
            "Example: Add \"minecraft:skeleton\" to make skeletons react to sounds."
        );

        if (!data.contains("soundattract.sounds")) {
            data.set("soundattract.sounds", Arrays.asList(
                "minecraft:block.lever.click",
                "minecraft:block.piston.extend",
                "minecraft:block.piston.contract",
                "minecraft:block.wooden_trapdoor.open"
            ));
        }
        data.setComment("soundattract.sounds",
            "List of sound events that attract these entities.\n" +
            "Example: Add \"minecraft:entity.creeper.primed\" to attract mobs when a creeper starts to explode.\n" +
            "Find valid sounds here: https://minecraft.fandom.com/wiki/Sounds.json#Java_Edition_values"
        );

        if (!data.contains("soundattract.hearingRadius")) {
            data.set("soundattract.hearingRadius", 32);
        }
        data.setComment("soundattract.hearingRadius",
            "Maximum distance (in blocks) at which mobs will notice an attracted sound."
        );

        if (!data.contains("soundattract.soundLifetimeTicks")) {
            data.set("soundattract.soundLifetimeTicks", 60);
        }
        data.setComment("soundattract.soundLifetimeTicks",
            "How long (in ticks) a sound remains 'interesting' after being heard. 20 ticks = 1 second."
        );

        if (!data.contains("soundattract.scanCooldownTicks")) {
            data.set("soundattract.scanCooldownTicks", 20);
        }
        data.setComment("soundattract.scanCooldownTicks",
            "How often (in ticks) the mob scans for new sounds. 20 = once per second."
        );

        if (!data.contains("soundattract.arrivalDistance")) {
            data.set("soundattract.arrivalDistance", 4.0);
        }
        data.setComment("soundattract.arrivalDistance",
            "The distance (in blocks) at which the mob is considered to have reached the sound.\n" +
            "Example: If set to 4.0, mobs will stop moving toward the sound when within 4 blocks."
        );

        data.save();

        ATTRACTED_ENTITIES = new ArrayList<>(data.get("soundattract.entities"));

        List<String> soundStrings = data.get("soundattract.sounds");
        Set<SoundEvent> tmpSounds = new HashSet<>();
        for (String s : soundStrings) {
            ResourceLocation rl = new ResourceLocation(s);
            SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            if (se != null) {
                tmpSounds.add(se);
            } else {
                System.out.println("[" + SoundAttractMod.MODID + "] Warning: '" + s + "' is not a valid sound event.");
            }
        }
        SOUNDS_TO_ATTRACT = tmpSounds;

        SOUND_HEARING_RADIUS = data.get("soundattract.hearingRadius");
        SOUND_LIFETIME_TICKS = data.get("soundattract.soundLifetimeTicks");
        SCAN_COOLDOWN_TICKS = data.get("soundattract.scanCooldownTicks");
        ARRIVAL_DISTANCE = data.get("soundattract.arrivalDistance");

        data.close();

    }
}
