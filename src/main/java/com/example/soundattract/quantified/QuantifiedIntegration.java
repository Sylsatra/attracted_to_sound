package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!ModList.get().isLoaded("quantified")) return;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            Method register = api.getMethod("register", String.class);
            register.invoke(null, SoundAttractMod.MOD_ID);
            
            try {
                Class<?> priorityManager = Class.forName("org.admany.quantified.core.common.async.task.ModPriorityManager");
                Method setMaxTasks = priorityManager.getMethod("setMaxTasksForMod", String.class, long.class);
                setMaxTasks.invoke(null, SoundAttractMod.MOD_ID, 1_000_000L);
            } catch (Throwable ignored) {
            }
            redirectJulLogging();
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Registration failed: {}", t.getMessage());
            }
        }
    }

    private static void redirectJulLogging() {
        try {
            java.util.logging.Logger jul = java.util.logging.Logger.getLogger("org.admany.quantified");
            jul.setUseParentHandlers(false);
            for (Handler h : jul.getHandlers()) {
                jul.removeHandler(h);
            }
            jul.setLevel(Level.ALL);
            jul.addHandler(new Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    if (!isLoggable(record)) return;
                    String msg = record.getMessage();
                    Throwable thrown = record.getThrown();
                    Level lvl = record.getLevel();
                    if (lvl.intValue() >= Level.SEVERE.intValue()) {
                        SoundAttractMod.LOGGER.error("[Quantified] {}", msg, thrown);
                    } else if (lvl.intValue() >= Level.WARNING.intValue()) {
                        SoundAttractMod.LOGGER.warn("[Quantified] {}", msg, thrown);
                    } else if (lvl.intValue() >= Level.INFO.intValue()) {
                        SoundAttractMod.LOGGER.info("[Quantified] {}", msg, thrown);
                    } else {
                        SoundAttractMod.LOGGER.debug("[Quantified] {}", msg, thrown);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Logging bridge failed: {}", t.getMessage());
            }
        }
    }
}
