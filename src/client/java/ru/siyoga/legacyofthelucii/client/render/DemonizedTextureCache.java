package ru.siyoga.legacyofthelucii.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DemonizedTextureCache {
    private static final Map<Identifier, Identifier> GENERATED = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();

    private DemonizedTextureCache() {
    }

    /**
     * Creates one GPU texture for each distinct original entity texture.
     * Several mobs using the same PNG reuse the same generated texture.
     */
    public static @Nullable Identifier getOrCreate(Identifier originalTexture) {
        Identifier cached = GENERATED.get(originalTexture);
        if (cached != null) {
            return cached;
        }

        if (FAILED.contains(originalTexture)) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Resource resource = client.getResourceManager()
                .getResource(originalTexture)
                .orElse(null);

        if (resource == null) {
            fail(originalTexture, "resource was not found", null);
            return null;
        }

        try (InputStream input = resource.getInputStream();
             NativeImage source = NativeImage.read(input)) {

            NativeImage transformed = new NativeImage(
                    source.getWidth(),
                    source.getHeight(),
                    false
            );

            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getColor(x, y);

                    int red = abgr & 0xFF;
                    int green = (abgr >>> 8) & 0xFF;
                    int blue = (abgr >>> 16) & 0xFF;
                    int alpha = (abgr >>> 24) & 0xFF;

                    if (alpha == 0) {
                        transformed.setColor(x, y, 0);
                        continue;
                    }

                    // Convert the original colors to luminance so texture
                    // details, shadows, faces and markings remain recognizable.
                    float luminance =
                            (red * 0.2126F + green * 0.7152F + blue * 0.0722F)
                                    / 255.0F;

                    // Slight curve compression keeps the result dark while
                    // retaining highlights on originally bright pixels.
                    float shade = (float) Math.pow(luminance, 0.78D);

                    int demonRed = clamp(Math.round(3.0F + 67.0F * shade));
                    int demonGreen = clamp(Math.round(1.0F + 11.0F * shade));
                    int demonBlue = clamp(Math.round(9.0F + 136.0F * shade));

                    // Bright original pixels receive a restrained magenta edge,
                    // producing demonic markings without an external PNG.
                    if (luminance > 0.72F) {
                        float highlight = (luminance - 0.72F) / 0.28F;
                        demonRed = clamp(demonRed + Math.round(25.0F * highlight));
                        demonBlue = clamp(demonBlue + Math.round(28.0F * highlight));
                    }

                    int recolored =
                            (alpha << 24)
                                    | (demonBlue << 16)
                                    | (demonGreen << 8)
                                    | demonRed;

                    transformed.setColor(x, y, recolored);
                }
            }

            NativeImageBackedTexture texture =
                    new NativeImageBackedTexture(transformed);

            String prefix = "demonized_"
                    + Integer.toUnsignedString(
                            originalTexture.toString().hashCode(),
                            16
                    );

            Identifier generated = client.getTextureManager()
                    .registerDynamicTexture(prefix, texture);

            texture.upload();
            GENERATED.put(originalTexture, generated);

            LegacyOfTheLucii.LOGGER.info(
                    "Demonization texture v7: generated {} -> {} ({}x{})",
                    originalTexture,
                    generated,
                    source.getWidth(),
                    source.getHeight()
            );

            return generated;
        } catch (Exception exception) {
            fail(originalTexture, "could not read or upload texture", exception);
            return null;
        }
    }

    public static boolean isGenerated(Identifier texture) {
        return GENERATED.containsKey(texture)
                || GENERATED.containsValue(texture);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void fail(
            Identifier originalTexture,
            String reason,
            @Nullable Exception exception
    ) {
        FAILED.add(originalTexture);

        if (exception == null) {
            LegacyOfTheLucii.LOGGER.warn(
                    "Demonization texture v7: {} for {}",
                    reason,
                    originalTexture
            );
        } else {
            LegacyOfTheLucii.LOGGER.warn(
                    "Demonization texture v7: {} for {}",
                    reason,
                    originalTexture,
                    exception
            );
        }
    }
}
