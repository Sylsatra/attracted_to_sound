package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.data.DataDrivenTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class CamoUtil {

    private CamoUtil() {}

    public static boolean isCamouflageArmorItem(Item item) {
        if (item == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return false;
        }

        boolean inConfig = SoundAttractConfig.COMMON.camouflageArmorItems.get().contains(id.toString());

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        if (!enableDataDriven) {
            return inConfig;
        }

        boolean inTag = false;
        try {
            inTag = new net.minecraft.world.item.ItemStack(item).is(DataDrivenTags.CAMOUFLAGE_ARMOR);
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[CamoUtil] Failed to check CAMOUFLAGE_ARMOR tag for {}: {}", id, e.getMessage());
            }
        }

        if (!inTag) {
            return inConfig;
        }

        String priority = SoundAttractConfig.COMMON.datapackPriority.get();
        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
        if (datapackOverConfig) {
            return inTag; // tags define the set, config is ignored for items missing the tag
        } else {
            return inConfig || inTag;
        }
    }
}
