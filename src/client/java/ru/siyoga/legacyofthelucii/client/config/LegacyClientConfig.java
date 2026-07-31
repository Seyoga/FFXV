package ru.siyoga.legacyofthelucii.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class LegacyClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("legacyofthelucii-client.json");

    private static LegacyClientConfig INSTANCE = new LegacyClientConfig();

    public String manaHudMode = "experience_bar";
    public boolean showManaText = false;
    public boolean showOnlyWhenRoyalArmsActive = true;

    private LegacyClientConfig() {
    }

    public static LegacyClientConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                LegacyClientConfig config = GSON.fromJson(reader, LegacyClientConfig.class);
                if (config != null) {
                    INSTANCE = config;
                }
            } catch (IOException ignored) {
                INSTANCE = new LegacyClientConfig();
            }
        }

        INSTANCE.normalize();
        save();
    }

    public boolean isManaHudEnabled() {
        return !"off".equals(manaHudMode.toLowerCase(Locale.ROOT));
    }

    public boolean useExperienceBarManaHud() {
        return "experience_bar".equals(manaHudMode.toLowerCase(Locale.ROOT));
    }

    private void normalize() {
        String normalizedMode = manaHudMode == null ? "experience_bar" : manaHudMode.toLowerCase(Locale.ROOT);
        if (!"compact".equals(normalizedMode)
                && !"experience_bar".equals(normalizedMode)
                && !"off".equals(normalizedMode)) {
            normalizedMode = "experience_bar";
        }

        manaHudMode = normalizedMode;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
