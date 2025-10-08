package com.example.soundattract.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

 

public class SoundAttractMixinPlugin implements IMixinConfigPlugin {

    private static boolean pbPresenceChecked = false;
    private static boolean pbPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        if (!pbPresenceChecked) {
            pbPresent = isResourceAvailable("com/vicmatskiv/pointblank/item/GunItem.class");
            pbPresenceChecked = true;
            System.out.println("[SoundAttractMixinPlugin] PointBlank class present: " + pbPresent);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.contains("PointBlank")) {
            return pbPresent || isResourceAvailable("com/vicmatskiv/pointblank/item/GunItem.class");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isResourceAvailable(String resourcePath) {
        try {
            return SoundAttractMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
