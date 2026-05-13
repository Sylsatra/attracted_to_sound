package com.example.soundattract.integration.relentlessundead;

import java.util.List;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;

public class RelentlessUndeadIntegration {

    public static boolean isLoaded() {
        return ModList.get().isLoaded("relentlessundead");
    }

    public static boolean getEffectiveZombiesIgnoreHeight() {
        if (isLoaded()) {
            try {
                return Inner.getZombiesIgnoreHeight();
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
            }
        }
        return SoundAttractConfig.COMMON.zombiesIgnoreHeight.get();
    }

    public static boolean getEffectiveZombiesCanStack() {
        if (isLoaded()) {
             try {
                return Inner.getZombiesCanStack();
             } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
             }
        }
        return SoundAttractConfig.COMMON.zombiesCanStack.get();
    }

    public static double getEffectiveZombieFallDamageMultiplier() {
        if (isLoaded()) {
             try {
                return Inner.getZombieFallDamageMultiplier();
             } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
             }
        }
        return SoundAttractConfig.COMMON.zombieFallDamageMultiplier.get();
    }

    public static boolean isMobEligibleForClimbing(Mob mob) {
         if (SoundAttractConfig.COMMON.enableRelentlessClimbing.get()) {
              if (isLoaded()) {
                   try {
                        return Inner.isMobEligible(mob);
                   } catch (Throwable t) {
                        SoundAttractMod.LOGGER.error("Failed to check Relentless Undead eligibility", t);
                   }
              } else {
                   List<? extends String> list = SoundAttractConfig.COMMON.relentlessEligibleMobs.get();
                   ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                   return mobId != null && list.contains(mobId.toString());
              }
         }
         return false;
    }

    private static class Inner {
         private static boolean getZombiesIgnoreHeight() {
             try {
                 Class<?> configClass = Class.forName("com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig");
                 Object common = configClass.getField("COMMON").get(null);
                 Class<?> holderClass = common.getClass();
                 Object zombiesIgnoreHeight = holderClass.getMethod("getZombiesIgnoreHeight").invoke(common);
                 return (Boolean) zombiesIgnoreHeight;
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }

         private static boolean getZombiesCanStack() {
             try {
                 Class<?> configClass = Class.forName("com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig");
                 Object common = configClass.getField("COMMON").get(null);
                 Class<?> holderClass = common.getClass();
                 Object zombiesCanStack = holderClass.getMethod("getZombiesCanStack").invoke(common);
                 return (Boolean) zombiesCanStack;
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }

         private static double getZombieFallDamageMultiplier() {
             try {
                 Class<?> configClass = Class.forName("com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig");
                 Object common = configClass.getField("COMMON").get(null);
                 Class<?> holderClass = common.getClass();
                 Object multiplier = holderClass.getMethod("getZombieFallDamageMultiplier").invoke(common);
                 return (Double) multiplier;
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }

         private static boolean isMobEligible(Mob mob) {
             try {
                 TagKey<EntityType<?>> hordeTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "hordes"));
                 return mob.getType().is(hordeTag);
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }
    }
}