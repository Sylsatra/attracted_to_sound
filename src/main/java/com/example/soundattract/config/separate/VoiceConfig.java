package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public class VoiceConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VOICE_CHAT_INTEGRATION;
    public static final ForgeConfigSpec.IntValue VOICE_CHAT_WHISPER_RANGE;
    public static final ForgeConfigSpec.IntValue VOICE_CHAT_NORMAL_RANGE;
    public static final ForgeConfigSpec.DoubleValue VOICE_CHAT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOICE_CHAT_DB_THRESHOLD_MAP;

    static {
        BUILDER.comment("Sound Attract Mod - Voice Chat Configuration").push("voice_chat");

        BUILDER.push("Simple VC");
        ENABLE_VOICE_CHAT_INTEGRATION = BUILDER.comment(
                "Enable Simple Voice Chat (SVC) integration.",
                "When enabled, voice chat frames generate a dynamic sound whose range scales with the audio's peak level (dBFS).",
                "Optional: Only takes effect if SVC is installed.")
                .define("enableVoiceChatIntegration", true);
        VOICE_CHAT_WHISPER_RANGE = BUILDER.comment("Base range used when whispering.")
                .defineInRange("voiceChatWhisperRange", 16, 1, 64);
        VOICE_CHAT_NORMAL_RANGE = BUILDER.comment("Base range used for normal speaking.")
                .defineInRange("voiceChatNormalRange", 32, 1, 128);
        VOICE_CHAT_WEIGHT = BUILDER.comment("Weight assigned to the generated SVC sound event.")
                .defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
        VOICE_CHAT_DB_THRESHOLD_MAP = BUILDER.comment(
                "Mapping from normalized dB thresholds to range multipliers.",
                "Format: 'threshold:multiplier'.",
                "Threshold is based on current peak volume level (usually 0-127)."
        ).defineList("voiceChatDbThresholdMap", java.util.Arrays.asList("110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"), obj -> obj instanceof String && ((String) obj).contains(":"));
        
        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
