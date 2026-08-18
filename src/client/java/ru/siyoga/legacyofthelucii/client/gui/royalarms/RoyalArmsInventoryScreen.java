package ru.siyoga.legacyofthelucii.client.gui.royalarms;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.royalarms.inventory.RoyalArmsScreenHandler;

public final class RoyalArmsInventoryScreen extends HandledScreen<RoyalArmsScreenHandler> {
    private static final Identifier NOCTIS_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/royal_arms/noctis.png"
    );
    private static final Identifier ARDYN_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/royal_arms/ardyn.png"
    );
    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 214;
    private static final int GUI_HEIGHT = 158;
    private static final int PLAYER_PANEL_X = 19;
    private static final int ARMIGER_PANEL_X = 46;
    private static final int ARMIGER_PANEL_WIDTH = 122;
    private static final int ARMIGER_PANEL_HEIGHT = 67;
    private static final int PLAYER_PANEL_Y = 68;
    private static final int PLAYER_PANEL_WIDTH = 176;
    private static final int PLAYER_PANEL_HEIGHT = 90;
    private static final int LEGEND_X = 198;
    private static final int AVAILABLE_LEGEND_Y = 75;
    private static final int LOCKED_LEGEND_Y = 96;
    private static final int LEGEND_U = 179;
    private static final int AVAILABLE_LEGEND_V = 75;
    private static final int LOCKED_LEGEND_V = 96;
    private static final int STORAGE_SLOT_X = 54;
    private static final int STORAGE_SLOT_Y = 8;

    public RoyalArmsInventoryScreen(
            RoyalArmsScreenHandler handler,
            PlayerInventory inventory,
            Text title
    ) {
        super(handler, inventory, title);
        backgroundWidth = GUI_WIDTH;
        backgroundHeight = GUI_HEIGHT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        renderLegendTooltip(context, mouseX, mouseY);
        renderLockedSlotTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        Identifier texture = texture();
        context.drawTexture(
                texture,
                x + ARMIGER_PANEL_X,
                y,
                0.0F,
                0.0F,
                ARMIGER_PANEL_WIDTH,
                ARMIGER_PANEL_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE
        );
        context.drawTexture(
                texture,
                x + PLAYER_PANEL_X,
                y + PLAYER_PANEL_Y,
                0.0F,
                PLAYER_PANEL_Y,
                PLAYER_PANEL_WIDTH,
                PLAYER_PANEL_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE
        );
        context.drawTexture(
                texture,
                x + LEGEND_X,
                y + AVAILABLE_LEGEND_Y,
                LEGEND_U,
                AVAILABLE_LEGEND_V,
                16,
                16,
                TEXTURE_SIZE,
                TEXTURE_SIZE
        );
        context.drawTexture(
                texture,
                x + LEGEND_X,
                y + LOCKED_LEGEND_Y,
                LEGEND_U,
                LOCKED_LEGEND_V,
                16,
                16,
                TEXTURE_SIZE,
                TEXTURE_SIZE
        );

        int unlocked = handler.unlockedSlots();
        for (int slot = unlocked; slot < RoyalArmsScreenHandler.STORAGE_SIZE; slot++) {
            int slotX = x + STORAGE_SLOT_X + slot % RoyalArmsScreenHandler.STORAGE_COLUMNS * 18;
            int slotY = y + STORAGE_SLOT_Y + slot / RoyalArmsScreenHandler.STORAGE_COLUMNS * 18;
            context.drawTexture(
                    texture,
                    slotX,
                    slotY,
                    LEGEND_U,
                    LOCKED_LEGEND_V,
                    16,
                    16,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE
            );
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    private void renderLegendTooltip(DrawContext context, int mouseX, int mouseY) {
        if (isInside(mouseX, mouseY, LEGEND_X, AVAILABLE_LEGEND_Y, 16, 16)) {
            context.drawTooltip(
                    textRenderer,
                    Text.translatable("screen.legacyofthelucii.royal_arms.available_slot"),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (isInside(mouseX, mouseY, LEGEND_X, LOCKED_LEGEND_Y, 16, 16)) {
            context.drawTooltip(
                    textRenderer,
                    Text.translatable("screen.legacyofthelucii.royal_arms.locked_slot"),
                    mouseX,
                    mouseY
            );
        }
    }

    private void renderLockedSlotTooltip(DrawContext context, int mouseX, int mouseY) {
        int relativeX = mouseX - x - STORAGE_SLOT_X;
        int relativeY = mouseY - y - STORAGE_SLOT_Y;
        if (relativeX < 0 || relativeY < 0) {
            return;
        }
        int column = relativeX / 18;
        int row = relativeY / 18;
        if (column < 0
                || column >= RoyalArmsScreenHandler.STORAGE_COLUMNS
                || row < 0
                || row >= RoyalArmsScreenHandler.STORAGE_ROWS
                || relativeX % 18 >= 16
                || relativeY % 18 >= 16) {
            return;
        }
        int slot = column + row * RoyalArmsScreenHandler.STORAGE_COLUMNS;
        if (!handler.isStorageSlotUnlocked(slot)) {
            context.drawTooltip(
                    textRenderer,
                    Text.translatable("screen.legacyofthelucii.royal_arms.locked_slot"),
                    mouseX,
                    mouseY
            );
        }
    }

    private boolean isInside(int mouseX, int mouseY, int offsetX, int offsetY, int width, int height) {
        int left = x + offsetX;
        int top = y + offsetY;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private static Identifier texture() {
        return ClientLuciiState.legacy() == LuciiLegacy.ARDYN ? ARDYN_TEXTURE : NOCTIS_TEXTURE;
    }
}
