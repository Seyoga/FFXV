package ru.siyoga.legacyofthelucii.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import ru.siyoga.legacyofthelucii.client.config.LegacyClientConfig;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsAbility;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class LuciiHudOverlay {
    private static final int XP_BAR_WIDTH = 182;
    private static final int XP_BAR_HEIGHT = 5;

    private LuciiHudOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(LuciiHudOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        LegacyClientConfig config = LegacyClientConfig.get();
        if (client.player == null
                || client.options.hudHidden
                || client.player.isCreative()
                || !ClientLuciiState.hasLegacy()
                || !config.isManaHudEnabled()
                || (config.showOnlyWhenRoyalArmsActive && !RoyalArmsAbility.isActive())) {
            return;
        }

        int color = manaColor();
        int fill = Math.round(XP_BAR_WIDTH * (ClientLuciiState.mana() / (float) ClientLuciiState.maxMana()));
        renderCompactMana(context, client, color, fill, config.showManaText);
    }

    private static int manaColor() {
        return ClientLuciiState.legacy() == LuciiLegacy.ARDYN ? 0xFFE54545 : 0xFF8FC8FF;
    }

    private static void renderCompactMana(DrawContext context, MinecraftClient client, int color, int fill, boolean showManaText) {
        int x = 8;
        int y = context.getScaledWindowHeight() - 48;
        int compactFill = Math.round(84 * (fill / (float) XP_BAR_WIDTH));
        context.fill(x - 1, y - 1, x + 85, y + 7, 0xAA000000);
        context.fill(x, y, x + 84, y + 6, 0x66000000);
        context.fill(x, y, x + compactFill, y + 6, color);
        if (showManaText) {
            String text = ClientLuciiState.mana() + "/" + ClientLuciiState.maxMana();
            context.drawTextWithShadow(client.textRenderer, text, x, y - 11, color);
        }
    }
}
