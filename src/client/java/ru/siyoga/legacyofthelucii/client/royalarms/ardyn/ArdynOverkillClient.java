package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.siyoga.legacyofthelucii.client.state.ArdynOverkillClientState;

public final class ArdynOverkillClient {
    private static final int FILTER_COLOR = 0x623B3547;
    private static final int EDGE_COLOR = 0x780C0710;

    private static float intensity;
    private static ClientPlayerEntity lastLocalPlayer;

    private ArdynOverkillClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ArdynOverkillClient::tick);
        HudRenderCallback.EVENT.register(ArdynOverkillClient::render);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != lastLocalPlayer) {
            onLocalPlayerChanged(player);
            lastLocalPlayer = player;
        }

        boolean active = player != null && ArdynOverkillClientState.active(player.getUuid());
        float target = active ? 1.0F : 0.0F;
        intensity = MathHelper.lerp(target > intensity ? 0.10F : 0.065F, intensity, target);
        if (Math.abs(target - intensity) < 0.002F) {
            intensity = target;
        }
    }

    private static void onLocalPlayerChanged(ClientPlayerEntity player) {
        // A respawn or reconnect replaces the local ClientPlayerEntity. The screen
        // overlay is rebuilt from the synchronized Overkill state on the next tick.
        // Withered hearts no longer rely on a temporary client-side status effect;
        // LivingEntityClientMixin asks ArdynOverkillClientState directly.
        intensity = player != null && ArdynOverkillClientState.active(player.getUuid())
                ? 1.0F
                : 0.0F;
    }

    public static void clearImmediately(MinecraftClient client) {
        intensity = 0.0F;
    }

    public static void reset(MinecraftClient client) {
        intensity = 0.0F;
        lastLocalPlayer = null;
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || intensity <= 0.002F) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        context.fill(0, 0, width, height, withScaledAlpha(FILTER_COLOR, intensity));
        renderVignette(context, width, height);
        renderAshNoise(context, client, width, height);
    }

    private static void renderVignette(DrawContext context, int width, int height) {
        int steps = 18;
        for (int i = 0; i < steps; i++) {
            float progress = 1.0F - i / (float) steps;
            float alpha = intensity * progress * progress;
            int color = withScaledAlpha(EDGE_COLOR, alpha);
            context.fill(i, i, width - i, i + 1, color);
            context.fill(i, height - i - 1, width - i, height - i, color);
            context.fill(i, i, i + 1, height - i, color);
            context.fill(width - i - 1, i, width - i, height - i, color);
        }
    }

    private static void renderAshNoise(DrawContext context, MinecraftClient client, int width, int height) {
        if (client.world == null) {
            return;
        }

        long time = client.world.getTime();
        int particles = Math.max(4, Math.round(11 * intensity));
        for (int i = 0; i < particles; i++) {
            int x = Math.floorMod((int) (time * 13L + i * 97L), Math.max(1, width));
            int y = Math.floorMod((int) (time * 7L + i * 53L), Math.max(1, height));
            int size = i % 4 == 0 ? 2 : 1;
            context.fill(
                    x,
                    y,
                    Math.min(width, x + size),
                    Math.min(height, y + size),
                    withScaledAlpha(0x66C8BECF, intensity)
            );
        }
    }

    private static int withScaledAlpha(int color, float scale) {
        int alpha = color >>> 24;
        int scaled = MathHelper.clamp(Math.round(alpha * scale), 0, 255);
        return (scaled << 24) | (color & 0x00FFFFFF);
    }
}
