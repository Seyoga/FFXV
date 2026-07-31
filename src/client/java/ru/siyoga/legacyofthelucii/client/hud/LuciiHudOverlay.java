package ru.siyoga.legacyofthelucii.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.config.LegacyClientConfig;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsAbility;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class LuciiHudOverlay {
    private static final int XP_BAR_WIDTH = 182;
    private static final int XP_BAR_HEIGHT = 5;
    private static final Identifier ARDYN_BAR_BACKGROUND = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/hud/royal_arms_bar_ardyn_background.png"
    );
    private static final Identifier ARDYN_BAR_PROGRESS = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/hud/royal_arms_bar_ardyn_progress.png"
    );
    private static final Identifier NOCTIS_BAR_BACKGROUND = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/hud/royal_arms_bar_noctis_background.png"
    );
    private static final Identifier NOCTIS_BAR_PROGRESS = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/hud/royal_arms_bar_noctis_progress.png"
    );

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

        if (config.useExperienceBarManaHud()) {
            return;
        }

        int color = manaColor();
        int fill = Math.round(XP_BAR_WIDTH * (ClientLuciiState.mana() / (float) ClientLuciiState.maxMana()));
        renderCompactMana(context, client, color, fill, config.showManaText);
    }

    public static boolean shouldReplaceExperienceBar(MinecraftClient client) {
        LegacyClientConfig config = LegacyClientConfig.get();
        return client.player != null
                && !client.options.hudHidden
                && !client.player.isCreative()
                && ClientLuciiState.hasLegacy()
                && config.isManaHudEnabled()
                && config.useExperienceBarManaHud()
                && (!config.showOnlyWhenRoyalArmsActive || RoyalArmsAbility.isActive());
    }

    public static void renderExperienceBarMana(DrawContext context, MinecraftClient client, int x) {
        int y = context.getScaledWindowHeight() - 32 + 3;
        int fill = Math.round(XP_BAR_WIDTH * (ClientLuciiState.mana() / (float) ClientLuciiState.maxMana()));
        renderManaBarTexture(context, x, y, fill);
        renderManaLevelText(context, client, x);
    }

    private static void renderManaBarTexture(DrawContext context, int x, int y, int fill) {
        if (ClientLuciiState.legacy() == LuciiLegacy.ARDYN) {
            context.drawTexture(ARDYN_BAR_BACKGROUND, x, y, 0, 0, XP_BAR_WIDTH, XP_BAR_HEIGHT, XP_BAR_WIDTH, XP_BAR_HEIGHT);
            if (fill > 0) {
                context.drawTexture(ARDYN_BAR_PROGRESS, x, y, 0, 0, fill, XP_BAR_HEIGHT, XP_BAR_WIDTH, XP_BAR_HEIGHT);
            }
            return;
        }

        context.drawTexture(NOCTIS_BAR_BACKGROUND, x, y, 0, 0, XP_BAR_WIDTH, XP_BAR_HEIGHT, XP_BAR_WIDTH, XP_BAR_HEIGHT);
        if (fill > 0) {
            context.drawTexture(NOCTIS_BAR_PROGRESS, x, y, 0, 0, fill, XP_BAR_HEIGHT, XP_BAR_WIDTH, XP_BAR_HEIGHT);
        }
    }

    private static void renderManaLevelText(DrawContext context, MinecraftClient client, int barX) {
        String text = ClientLuciiState.legacy() == LuciiLegacy.ARDYN
                ? String.valueOf(ClientLuciiState.ardynWarpCharges())
                : String.valueOf(ClientLuciiState.mana());
        int textX = (context.getScaledWindowWidth() - client.textRenderer.getWidth(text)) / 2;
        int textY = context.getScaledWindowHeight() - 31 - 4;
        context.drawText(client.textRenderer, text, textX + 1, textY, 0, false);
        context.drawText(client.textRenderer, text, textX - 1, textY, 0, false);
        context.drawText(client.textRenderer, text, textX, textY + 1, 0, false);
        context.drawText(client.textRenderer, text, textX, textY - 1, 0, false);
        context.drawText(client.textRenderer, text, textX, textY, manaColor(), false);
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
