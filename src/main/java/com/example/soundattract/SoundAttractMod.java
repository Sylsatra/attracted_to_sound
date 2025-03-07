package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("soundattractmod")
public class SoundAttractMod {
    public static final String MODID = "soundattractmod";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public SoundAttractMod() {
        SoundAttractConfig.loadConfig();

        SoundAttractNetwork.init();

        LOGGER.info("[{}] Mod constructor complete.", MODID);
    }
}
