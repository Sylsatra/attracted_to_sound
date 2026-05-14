package com.example.soundattract.component;

import com.example.soundattract.camo.CamouflageCapability;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.world.entity.LivingEntity;

public final class EntityComponents implements EntityComponentInitializer {

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(LivingEntity.class, ModComponents.CAMOUFLAGE)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(entity -> new CamouflageCapability());
    }
}
