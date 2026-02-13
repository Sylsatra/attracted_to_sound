package com.example.soundattract.camo;

import com.example.soundattract.config.separate.StealthConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.AbstractTexture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import com.example.soundattract.mixin.NativeImageAccessor;

public class CamoTextureGenerator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ResourceLocation, NativeImage> CAPTURED_MASKS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> READBACK_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<ResourceLocation, ResourceLocation> REDIRECT_CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation NULL_LOCATION = ResourceLocation.tryBuild("soundattract", "null");

    public static void recordCapturedMask(ResourceLocation loc, NativeImage img) {
        NativeImage old = CAPTURED_MASKS.put(loc, img);
        if (old != null && old != img) {
            old.close();
        }
    }

    public static void releaseCapturedMask(ResourceLocation loc) {
        NativeImage img = CAPTURED_MASKS.remove(loc);
        if (img != null) {
            img.close();
        }
    }

    private static final Map<TextureKey, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<MaskedTextureKey, ResourceLocation> MASKED_CACHE = new ConcurrentHashMap<>();

    public static ResourceLocation getOrCreateSmudge(long seed, int color, float strength, float erosion, float humidity, float temp) {
        return getOrCreateSmudge(seed, color, strength, StealthConfig.CAMO_TEXTURE_RESOLUTION.get(), erosion, humidity, temp);
    }

    public static ResourceLocation getOrCreateSmudge(long seed, int color, float strength, int resolution, float erosion, float humidity, float temp) {
        float strengthTier = Math.round(strength * 50.0f) / 50.0f;
        TextureKey key = new TextureKey(seed, color, strengthTier, resolution, erosion, humidity, temp);

        ResourceLocation existing = CACHE.get(key);
        if (existing != null) {
            return existing;
        }

        if (CACHE.size() > 256) {
            clearCaches();
        }

        ResourceLocation loc = generateTexture(key);
        CACHE.put(key, loc);
        return loc;
    }

    public static ResourceLocation getOrCreateMaskedSmudge(long seed, int color, float strength, ResourceLocation maskLoc, int resolution, float erosion, float humidity, float temp) {
        return getOrCreateMaskedSmudge(seed, color, strength, Collections.singletonList(maskLoc), resolution, erosion, humidity, temp);
    }

    public static ResourceLocation getOrCreateMaskedSmudge(long seed, int color, float strength, java.util.List<ResourceLocation> maskLocs, int resolution, float erosion, float humidity, float temp) {
        float strengthTier = Math.round(strength * 50.0f) / 50.0f;
        MaskedTextureKey key = new MaskedTextureKey(seed, color, strengthTier, maskLocs, resolution, erosion, humidity, temp);

        ResourceLocation existing = MASKED_CACHE.get(key);
        if (existing != null) {
            return existing == NULL_LOCATION ? null : existing;
        }

        if (MASKED_CACHE.size() > 256) {
            clearCaches();
        }

        ResourceLocation loc = generateMaskedTexture(key);
        MASKED_CACHE.put(key, loc == null ? NULL_LOCATION : loc);
        return loc;
    }

    public static void clearCaches() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Runnable clear = () -> {
            for (ResourceLocation loc : CACHE.values()) {
                mc.getTextureManager().release(loc);
            }
            CACHE.clear();

            for (ResourceLocation loc : MASKED_CACHE.values()) {
                mc.getTextureManager().release(loc);
            }
            MASKED_CACHE.clear();
            REDIRECT_CACHE.clear();
            READBACK_FAILURES.clear();
        };

        if (RenderSystem.isOnRenderThread()) {
            clear.run();
        } else {
            mc.execute(clear);
        }
    }

    private static NativeImage extractAlphaMask(ResourceLocation loc) {
        if (loc == null || READBACK_FAILURES.contains(loc)) return null;
        
        if (loc.getNamespace().equals("minecraft") && !loc.getPath().startsWith("textures/") && !loc.getPath().startsWith("skins/")) {
             READBACK_FAILURES.add(loc);
             return null;
        }

        try {

            NativeImage captured = CAPTURED_MASKS.get(loc);
            if (captured != null) {
                NativeImage copy = new NativeImage(captured.getWidth(), captured.getHeight(), false);
                copy.copyFrom(captured);
                return copy;
            }


            var resource = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                return NativeImage.read(resource.get().open());
            }



        if (RenderSystem.isOnRenderThread()) {
                ResourceLocation targetLoc = loc;
                
                if (targetLoc.getNamespace().equals("minecraft")) {
                    ResourceLocation redirected = REDIRECT_CACHE.get(targetLoc);
                    if (redirected != null) {
                        targetLoc = redirected;
                    } else if (Minecraft.getInstance().getResourceManager().getResource(targetLoc).isEmpty()) {
                        String path = targetLoc.getPath();
                        for (net.minecraftforge.forgespi.language.IModInfo mod : net.minecraftforge.fml.ModList.get().getMods()) {
                            ResourceLocation tryLoc = ResourceLocation.tryBuild(mod.getModId(), path);
                            if (Minecraft.getInstance().getResourceManager().getResource(tryLoc).isPresent()) {
                                REDIRECT_CACHE.put(targetLoc, tryLoc);
                                targetLoc = tryLoc;
                                break;
                            }
                        }
                    }
                }

                boolean exists = Minecraft.getInstance().getResourceManager().getResource(targetLoc).isPresent();
                if (!exists) {
                     if (Minecraft.getInstance().getTextureManager().getTexture(targetLoc) == null) {
                         READBACK_FAILURES.add(loc);
                         return null;
                     }
                }

                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(targetLoc);
                if (texture != null) {
                    int glId = texture.getId();
                    if (glId > 0) {
                        RenderSystem.bindTexture(glId);
                        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                        
                        if (width > 0 && height > 0 && width <= 4096 && height <= 4096) {
                            if (width <= 16 && height <= 16) {
                                READBACK_FAILURES.add(loc);
                                return null;
                            }

                            NativeImage img = new NativeImage(width, height, false);
                            long pointer = ((NativeImageAccessor)(Object)img).getPixelsPointer();
                            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pointer);
                            
                            int p00 = img.getPixelRGBA(0, 0);

                            NativeImage cacheCopy = new NativeImage(width, height, false);
                            cacheCopy.copyFrom(img);
                            CAPTURED_MASKS.put(loc, cacheCopy);
                            return img;
                        } else if (width == 0 || height == 0) {
                            return null;
                        } else {
                            LOGGER.debug("[Camo] Failed GPU readback (Invalid dimensions {}x{}): {}", width, height, loc);
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        READBACK_FAILURES.add(loc);
        return null;
    }

    private static ResourceLocation generateMaskedTexture(MaskedTextureKey key) {
        if (key.maskLocs == null || key.maskLocs.isEmpty()) return null;

        java.util.List<NativeImage> masks = new java.util.ArrayList<>();
        int maxWidth = 0;
        int maxHeight = 0;

        for (ResourceLocation loc : key.maskLocs) {
            NativeImage m = extractAlphaMask(loc);
            if (m != null) {
                masks.add(m);
                maxWidth = Math.max(maxWidth, m.getWidth());
                maxHeight = Math.max(maxHeight, m.getHeight());
            }
        }

        if (masks.isEmpty()) return null;

        final NativeImage masterMask;
        if (masks.size() == 1) {
            masterMask = masks.get(0);
        } else {
            masterMask = new NativeImage(maxWidth, maxHeight, true);
            for (NativeImage m : masks) {
                for (int mx = 0; mx < maxWidth; mx++) {
                    for (int my = 0; my < maxHeight; my++) {
                        int srcX = (int) ((mx / (float) maxWidth) * m.getWidth());
                        int srcY = (int) ((my / (float) maxHeight) * m.getHeight());
                        
                        int srcPixel = m.getPixelRGBA(srcX, srcY);
                        int srcAlpha = (srcPixel >> 24) & 0xFF;
                        
                        if (srcAlpha > 0) {
                            int existingPixel = masterMask.getPixelRGBA(mx, my);
                            int existingAlpha = (existingPixel >> 24) & 0xFF;
                            
                            if (srcAlpha > existingAlpha) {
                                masterMask.setPixelRGBA(mx, my, (srcAlpha << 24) | (srcPixel & 0xFFFFFF));
                            }
                        }
                    }
                }
                if (m != masterMask) m.close();
            }
        }

        int width = key.resolution;
        int height = key.resolution;
        
        width = Math.max(16, Math.min(width, 4096));
        height = Math.max(16, Math.min(height, 4096));

        NativeImage image = new NativeImage(width, height, true);
        Random random = new Random(key.seed);


        float[][] noise = new float[width][height];
        float min = 1.0f, max = 0.0f;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                noise[x][y] = random.nextFloat();
            }
        }


        float[][] nextNoise = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float sum = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        sum += noise[(x + dx + width) % width][(y + dy + height) % height];
                    }
                }
                nextNoise[x][y] = sum / 9.0f;
                min = Math.min(min, nextNoise[x][y]);
                max = Math.max(max, nextNoise[x][y]);
            }
        }
        noise = nextNoise;


        boolean jagged = key.erosion < 0;
        boolean humid = key.humidity > 0;
        
        float threshold = 0.95f - (key.strength * 0.8f);
        int r = (key.color >> 16) & 0xFF;
        int g = (key.color >> 8) & 0xFF;
        int b = key.color & 0xFF;
        float range = (max - min) + 0.0001f;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int maskX = (int) ((x / (float) width) * masterMask.getWidth());
                int maskY = (int) ((y / (float) height) * masterMask.getHeight());
                int maskPixel = masterMask.getPixelRGBA(maskX, maskY);
                int maskAlpha = (maskPixel >> 24) & 0xFF;
                if (maskAlpha == 0) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }

                float rawNorm = (noise[x][y] - min) / range;
                float finalVal;

                if (jagged) {
                    if (humid) {
                        finalVal = (float) Math.pow(rawNorm, 3.0);
                    } else {
                        finalVal = Math.round(rawNorm * 4.0f) / 4.0f;
                    }
                } else {
                    if (humid) {
                        finalVal = (float) Math.pow(rawNorm, 1.5);
                    } else {
                        float ripple = (float) Math.sin(x * 0.5 + y * 0.2);
                        finalVal = (rawNorm * 0.7f) + (ripple * 0.3f);
                    }
                }

                if (finalVal >= threshold) {
                    float alphaStep = (finalVal - threshold) / (1.0f - threshold + 0.001f);
                    int alpha = (int) (Math.min(0.75f, key.strength * 0.8f) * 255 * (0.5f + alphaStep * 0.5f));
                    alpha = (int) (alpha * (maskAlpha / 255.0f));
                    int abgr = (alpha << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelRGBA(x, y, abgr);
                } else {
                    image.setPixelRGBA(x, y, 0);
                }
            }
        }

        if (masterMask != null) masterMask.close();
        DynamicTexture texture = new DynamicTexture(image);
        int strengthCode = (int)(key.strength * 10);
        String maskHash = String.valueOf(key.maskLocs.hashCode());
        String name = "generated/camo_masked/" + Math.abs(key.seed) + "_" + strengthCode + "_" + key.color + "_" + maskHash + "_" + key.resolution;
        ResourceLocation loc = ResourceLocation.tryBuild("soundattract", name.toLowerCase().replaceAll("[^a-z0-9_/.]", "_"));
        Minecraft.getInstance().getTextureManager().register(loc, texture);
        return loc;
    }

    private static ResourceLocation generateTexture(TextureKey key) {
        int res = key.resolution;
        NativeImage image = new NativeImage(res, res, true);
        Random random = new Random(key.seed);


        float[][] noise = new float[res][res];
        float min = 1.0f;
        float max = 0.0f;
        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                noise[x][y] = random.nextFloat();
            }
        }


        for (int pass = 0; pass < 2; pass++) {
            float[][] nextNoise = new float[res][res];
            for (int x = 0; x < res; x++) {
                for (int y = 0; y < res; y++) {
                    float sum = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            sum += noise[(x + dx + res) % res][(y + dy + res) % res];
                        }
                    }
                    nextNoise[x][y] = sum / 9.0f;
                }
            }
            noise = nextNoise;
        }


        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                min = Math.min(min, noise[x][y]);
                max = Math.max(max, noise[x][y]);
            }
        }



        boolean jagged = key.erosion < 0;
        boolean humid = key.humidity > 0;
        
        float threshold = 0.95f - (key.strength * 0.8f);
        int r = (key.color >> 16) & 0xFF;
        int g = (key.color >> 8) & 0xFF;
        int b = key.color & 0xFF;
        float range = (max - min) + 0.0001f;

        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                float rawNorm = (noise[x][y] - min) / range;
                float finalVal;

                if (jagged) {
                    if (humid) finalVal = (float) Math.pow(rawNorm, 3.0);
                    else finalVal = Math.round(rawNorm * 4.0f) / 4.0f;
                } else {
                    if (humid) finalVal = (float) Math.pow(rawNorm, 1.5);
                    else { 
                        float ripple = (float) Math.sin(x * 0.5 + y * 0.2);
                        finalVal = (rawNorm * 0.7f) + (ripple * 0.3f);
                    }
                }

                if (finalVal >= threshold) {
                    float alphaStep = (finalVal - threshold) / (1.0f - threshold + 0.001f);
                    int alpha = (int) (Math.min(0.75f, key.strength * 0.8f) * 255 * (0.5f + alphaStep * 0.5f));
                    int abgr = (alpha << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelRGBA(x, y, abgr);
                } else {
                    image.setPixelRGBA(x, y, 0);
                }
            }
        }

        DynamicTexture texture = new DynamicTexture(image);

        int strengthCode = (int)(key.strength * 10);
        String name = "generated/camo/" + Math.abs(key.seed) + "_" + strengthCode + "_" + key.color;
        ResourceLocation loc = ResourceLocation.tryBuild("soundattract", name.toLowerCase().replaceAll("[^a-z0-9_/.]", "_"));
        Minecraft.getInstance().getTextureManager().register(loc, texture);
        return loc;
    }

    private record TextureKey(long seed, int color, float strength, int resolution, float erosion, float humidity, float temp) {}
    private record MaskedTextureKey(long seed, int color, float strength, java.util.List<ResourceLocation> maskLocs, int resolution, float erosion, float humidity, float temp) {}
}
