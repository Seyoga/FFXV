package ru.siyoga.legacyofthelucii.client.gui.skilltree;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class SkillTreeScreen extends Screen {
    private static final Identifier NOCTIS_BACKGROUND = new Identifier(LegacyOfTheLucii.MOD_ID, "textures/gui/skill_tree/noctis.png");
    private static final Identifier ARDYN_BACKGROUND = new Identifier(LegacyOfTheLucii.MOD_ID, "textures/gui/skill_tree/ardyn.png");
    private static final Identifier WINDOW_TEXTURE = new Identifier(LegacyOfTheLucii.MOD_ID, "textures/gui/skill_tree/window.png");
    private static final Identifier TASK_FRAME_OBTAINED = new Identifier(LegacyOfTheLucii.MOD_ID, "textures/gui/skill_tree/task_frame_obtained.png");
    private static final int TEXTURE_WIDTH = 1920;
    private static final int TEXTURE_HEIGHT = 1080;
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int PAGE_OFFSET_X = 9;
    private static final int PAGE_OFFSET_Y = 18;
    private static final int PAGE_WIDTH = 234;
    private static final int PAGE_HEIGHT = 113;
    private static final int TITLE_OFFSET_X = 8;
    private static final int TITLE_OFFSET_Y = 6;
    private static final int TILE_WIDTH = 468;
    private static final int TILE_HEIGHT = 263;
    private static final int EDGE_FADE_WIDTH = 12;
    private static final int EDGE_FADE_MAX_ALPHA = 0x68;
    private static final int SKILL_FRAME_SIZE = 26;

    private double scrollX;
    private double scrollY;

    public SkillTreeScreen() {
        super(Text.translatable("screen.legacyofthelucii.skill_tree"));
    }

    public static void open(MinecraftClient client) {
        if (client.player == null) return;
        LuciiLegacy legacy = ClientLuciiState.legacy();
        if (legacy != LuciiLegacy.NOCTIS && legacy != LuciiLegacy.ARDYN) {
            client.player.sendMessage(Text.translatable("message.legacyofthelucii.skill_tree.requires_legacy"), true);
            return;
        }
        client.setScreen(new SkillTreeScreen());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int windowX = (width - WINDOW_WIDTH) / 2;
        int windowY = (height - WINDOW_HEIGHT) / 2;
        int viewportX = windowX + PAGE_OFFSET_X;
        int viewportY = windowY + PAGE_OFFSET_Y;
        renderWindow(context, windowX, windowY);
        context.drawText(textRenderer, title, windowX + TITLE_OFFSET_X, windowY + TITLE_OFFSET_Y, 0xFF404040, false);
        context.enableScissor(viewportX, viewportY, viewportX + PAGE_WIDTH, viewportY + PAGE_HEIGHT);
        renderSkillCanvas(context, viewportX, viewportY);
        renderSkills(context, viewportX, viewportY, mouseX, mouseY);
        renderPageEdgeFade(context, viewportX, viewportY);
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) { scrollX += deltaX; scrollY += deltaY; return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { scrollY += amount * 24.0D; return true; }

    @Override
    public boolean shouldPause() { return false; }

    private void renderWindow(DrawContext context, int x, int y) {
        context.drawTexture(WINDOW_TEXTURE, x, y, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void renderSkillCanvas(DrawContext context, int x, int y) {
        Identifier texture = backgroundFor(ClientLuciiState.legacy());
        int startX = x + floorMod((int) Math.round(scrollX), TILE_WIDTH) - TILE_WIDTH;
        int startY = y + floorMod((int) Math.round(scrollY), TILE_HEIGHT) - TILE_HEIGHT;
        for (int drawX = startX; drawX < x + PAGE_WIDTH; drawX += TILE_WIDTH) {
            for (int drawY = startY; drawY < y + PAGE_HEIGHT; drawY += TILE_HEIGHT) {
                context.drawTexture(texture, drawX, drawY, TILE_WIDTH, TILE_HEIGHT, 0.0F, 0.0F,
                        TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }
        }
    }

    private void renderSkills(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int rowY = y + PAGE_HEIGHT / 2 - SKILL_FRAME_SIZE / 2 + (int) Math.round(scrollY);
        if (ClientLuciiState.legacy() == LuciiLegacy.ARDYN) {
            renderSkillNode(context, x + PAGE_WIDTH / 2 - 55 + (int) Math.round(scrollX), rowY, Items.ENDER_EYE.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.shadow_step"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 - 13 + (int) Math.round(scrollX), rowY, Items.ENDER_PEARL.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.warp"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 + 29 + (int) Math.round(scrollX), rowY, Items.CROSSBOW.getDefaultStack(), Text.translatable("skill.legacyofthelucii.royal_arms.bind"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 + 71 + (int) Math.round(scrollX), rowY, Items.SPECTRAL_ARROW.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.barrage"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 + 71 + (int) Math.round(scrollX), rowY + 34, Items.WITHER_SKELETON_SKULL.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.overkill"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 + 29 + (int) Math.round(scrollX), rowY + 34, Items.FIRE_CHARGE.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.dark_tornado"), mouseX, mouseY);
            renderSkillNode(context, x + PAGE_WIDTH / 2 - 13 + (int) Math.round(scrollX), rowY + 34, Items.CARVED_PUMPKIN.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.masquerade"), mouseX, mouseY);
            return;
            renderSkillNode(context, x + PAGE_WIDTH / 2 - 55 + (int) Math.round(scrollX), rowY + 34, Items.SPYGLASS.getDefaultStack(), Text.translatable("skill.legacyofthelucii.ardyn.cerberus_0"), mouseX, mouseY);
        }
        if (ClientLuciiState.legacy() != LuciiLegacy.NOCTIS) return;
        renderSkillNode(context, x + PAGE_WIDTH / 2 - 55 + (int) Math.round(scrollX), rowY, Items.IRON_BLOCK.getDefaultStack(), Text.translatable("skill.legacyofthelucii.noctis.wall"), mouseX, mouseY);
        renderSkillNode(context, x + PAGE_WIDTH / 2 - 13 + (int) Math.round(scrollX), rowY, Items.ENDER_PEARL.getDefaultStack(), Text.translatable("skill.legacyofthelucii.noctis.warp"), mouseX, mouseY);
        renderSkillNode(context, x + PAGE_WIDTH / 2 + 29 + (int) Math.round(scrollX), rowY, Items.CROSSBOW.getDefaultStack(), Text.translatable("skill.legacyofthelucii.royal_arms.bind"), mouseX, mouseY);
        renderSkillNode(context, x + PAGE_WIDTH / 2 + 71 + (int) Math.round(scrollX), rowY, Items.SHIELD.getDefaultStack(), Text.translatable("skill.legacyofthelucii.noctis.guard"), mouseX, mouseY);
    }

    private void renderSkillNode(DrawContext context, int nodeX, int nodeY, ItemStack icon, Text tooltip, int mouseX, int mouseY) {
        context.drawTexture(TASK_FRAME_OBTAINED, nodeX, nodeY, 0, 0, SKILL_FRAME_SIZE, SKILL_FRAME_SIZE, SKILL_FRAME_SIZE, SKILL_FRAME_SIZE);
        context.drawItem(icon, nodeX + 5, nodeY + 5);
        if (mouseX >= nodeX && mouseX < nodeX + SKILL_FRAME_SIZE && mouseY >= nodeY && mouseY < nodeY + SKILL_FRAME_SIZE) {
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private void renderPageEdgeFade(DrawContext context, int x, int y) {
        for (int offset = 0; offset < EDGE_FADE_WIDTH; offset++) {
            float progress = 1.0F - offset / (float) EDGE_FADE_WIDTH;
            int alpha = (int) (EDGE_FADE_MAX_ALPHA * progress * progress);
            int color = alpha << 24;
            context.fill(x + offset, y, x + offset + 1, y + PAGE_HEIGHT, color);
            context.fill(x + PAGE_WIDTH - offset - 1, y, x + PAGE_WIDTH - offset, y + PAGE_HEIGHT, color);
            context.fill(x, y + offset, x + PAGE_WIDTH, y + offset + 1, color);
            context.fill(x, y + PAGE_HEIGHT - offset - 1, x + PAGE_WIDTH, y + PAGE_HEIGHT - offset, color);
        }
    }

    private static Identifier backgroundFor(LuciiLegacy legacy) { return legacy == LuciiLegacy.ARDYN ? ARDYN_BACKGROUND : NOCTIS_BACKGROUND; }
    private static int floorMod(int value, int divisor) { int result = value % divisor; return result < 0 ? result + divisor : result; }
}
