package ru.siyoga.legacyofthelucii.client.gui.masquerade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeClient;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeClientState;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeRenderEntityCache;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeRenderer;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MasqueradeScreen extends Screen {
    private static final Identifier BACKGROUND = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/masquerade/masquerade_gui.png"
    );
    private static final Identifier SCROLLER = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/masquerade/scroller.png"
    );
    private static final Identifier SCROLLER_DISABLED = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/masquerade/scroller_disabled.png"
    );
    private static final int GUI_WIDTH = 198;
    private static final int GUI_HEIGHT = 118;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 19;
    private static final int LIST_WIDTH = 89;
    private static final int LIST_HEIGHT = 87;
    private static final int ROW_HEIGHT = 18;
    private static final int SELECTED_HIGHLIGHT_X_OFFSET = 1;
    private static final int HOVER_HIGHLIGHT_X_OFFSET = 2;
    private static final int SCROLLER_X = 103;
    private static final int SCROLLER_Y = 18;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    private static final int SCROLLER_TRACK_HEIGHT = 88;
    private static final int PREVIEW_LEFT = 121;
    private static final int PREVIEW_TOP = 18;
    private static final int PREVIEW_WIDTH = 71;
    private static final int PREVIEW_HEIGHT = 88;

    private final List<MasqueradeMorph> filteredMorphs = new ArrayList<>();
    private TextFieldWidget searchField;
    private double scrollAmount;
    private boolean draggingScroller;
    private double scrollerDragOffset;
    private boolean hasPendingSelection;
    private MasqueradeMorph pendingSelection;
    private int knownStateRevision;

    private MasqueradeScreen() {
        super(Text.translatable("screen.legacyofthelucii.masquerade"));
    }

    public static void open(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (ClientLuciiState.legacy() != LuciiLegacy.ARDYN) {
            client.player.sendMessage(
                    Text.translatable("message.legacyofthelucii.masquerade.requires_ardyn"),
                    true
            );
            return;
        }
        if (MasqueradeClientState.localTargetUuid() == null) {
            client.player.sendMessage(
                    Text.translatable("message.legacyofthelucii.masquerade.requires_target"),
                    true
            );
            return;
        }
        client.setScreen(new MasqueradeScreen());
    }

    @Override
    protected void init() {
        int x = guiX();
        int y = guiY();
        searchField = new TextFieldWidget(
                textRenderer,
                x + 10,
                y + 6,
                88,
                11,
                Text.translatable("screen.legacyofthelucii.masquerade.search")
        );
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(64);
        searchField.setChangedListener(value -> rebuildFilteredMorphs());
        searchField.setPlaceholder(Text.translatable("screen.legacyofthelucii.masquerade.search"));
        addDrawableChild(searchField);
        setInitialFocus(searchField);
        knownStateRevision = MasqueradeClientState.revision();
        rebuildFilteredMorphs();
    }

    @Override
    public void tick() {
        searchField.tick();
        if (knownStateRevision != MasqueradeClientState.revision()) {
            knownStateRevision = MasqueradeClientState.revision();
            if (hasPendingSelection
                    && Objects.equals(pendingSelection, MasqueradeClientState.localActiveMorph())) {
                hasPendingSelection = false;
            }
            rebuildFilteredMorphs();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int x = guiX();
        int y = guiY();
        context.drawTexture(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
        renderMorphList(context, x, y, mouseX, mouseY);
        renderScroller(context, x, y);
        renderPreview(context, x, y, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = guiX();
            int y = guiY();
            if (isInside(mouseX, mouseY, x + LIST_X, y + LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
                int index = (int) ((mouseY - (y + LIST_Y) + scrollAmount) / ROW_HEIGHT);
                if (index >= 0 && index < filteredMorphs.size()) {
                    select(filteredMorphs.get(index));
                    return true;
                }
            }
            if (isInside(mouseX, mouseY, x + SCROLLER_X, y + SCROLLER_Y,
                    SCROLLER_WIDTH, SCROLLER_TRACK_HEIGHT) && maxScroll() > 0.0D) {
                int thumbY = scrollerThumbY(y);
                if (mouseY >= thumbY && mouseY < thumbY + SCROLLER_HEIGHT) {
                    scrollerDragOffset = mouseY - thumbY;
                } else {
                    scrollerDragOffset = SCROLLER_HEIGHT / 2.0D;
                    setScrollFromScroller(mouseY, y);
                }
                draggingScroller = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (button == 0 && draggingScroller) {
            setScrollFromScroller(mouseY, guiY());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScroller) {
            draggingScroller = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0.0D) {
            scrollAmount = MathHelper.clamp(scrollAmount - amount * ROW_HEIGHT, 0.0D, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void rebuildFilteredMorphs() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredMorphs.clear();
        for (MasqueradeMorph morph : MasqueradeClientState.unlockedMorphs()) {
            if (query.isEmpty() || displayName(morph).getString().toLowerCase(Locale.ROOT).contains(query)) {
                filteredMorphs.add(morph);
            }
        }
        filteredMorphs.sort(Comparator.comparing(morph -> displayName(morph).getString(), String.CASE_INSENSITIVE_ORDER));
        scrollAmount = MathHelper.clamp(scrollAmount, 0.0D, maxScroll());
    }

    private void renderMorphList(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int listX = x + LIST_X;
        int listY = y + LIST_Y;
        context.enableScissor(listX, listY, listX + LIST_WIDTH, listY + LIST_HEIGHT);
        MasqueradeMorph selected = selectedMorph();
        for (int index = 0; index < filteredMorphs.size(); index++) {
            int rowY = listY + index * ROW_HEIGHT - (int) scrollAmount;
            if (rowY + ROW_HEIGHT <= listY || rowY >= listY + LIST_HEIGHT) {
                continue;
            }
            MasqueradeMorph morph = filteredMorphs.get(index);
            boolean hovered = isInside(mouseX, mouseY, listX, rowY, LIST_WIDTH, ROW_HEIGHT);
            if (morph.equals(selected)) {
                context.fill(listX + SELECTED_HIGHLIGHT_X_OFFSET, rowY,
                        listX + LIST_WIDTH, rowY + ROW_HEIGHT, 0xA0603040);
                context.fill(listX + SELECTED_HIGHLIGHT_X_OFFSET, rowY,
                        listX + SELECTED_HIGHLIGHT_X_OFFSET + 2, rowY + ROW_HEIGHT, 0xFFFFC6D2);
            } else if (hovered) {
                context.fill(listX + HOVER_HIGHLIGHT_X_OFFSET, rowY,
                        listX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x704A4A4A);
            }
            renderMorphIcon(context, morph, listX + 9, rowY + 16);
            String name = textRenderer.trimToWidth(displayName(morph).getString(), LIST_WIDTH - 23);
            context.drawText(textRenderer, name, listX + 21, rowY + 5, 0xFFE8E8E8, false);
        }
        context.disableScissor();
    }

    private void renderMorphIcon(DrawContext context, MasqueradeMorph morph, int centerX, int bottomY) {
        if (client == null || client.player == null) {
            return;
        }
        LivingEntity entity = MasqueradeRenderEntityCache.get(morph, client.player);
        if (entity == null) {
            return;
        }
        int scale = fittedScale(entity, 12, 8)
                * (morph.kind() == MasqueradeMorph.Kind.ENTITY ? 2 : 1);
        MasqueradeRenderer.renderWithoutPlayerReplacement(() ->
                InventoryScreen.drawEntity(context, centerX, bottomY, scale, 0.0F, 0.0F, entity));
    }

    private void renderScroller(DrawContext context, int x, int y) {
        Identifier texture = maxScroll() > 0.0D ? SCROLLER : SCROLLER_DISABLED;
        context.drawTexture(texture, x + SCROLLER_X, scrollerThumbY(y), 0, 0,
                SCROLLER_WIDTH, SCROLLER_HEIGHT, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    private void renderPreview(DrawContext context, int x, int y, int mouseX, int mouseY) {
        if (client == null || client.player == null) {
            return;
        }
        MasqueradeMorph selected = selectedMorph();
        LivingEntity preview = selected == null
                ? client.player
                : MasqueradeRenderEntityCache.get(selected, client.player);
        if (preview == null) {
            return;
        }

        int centerX = x + PREVIEW_LEFT + PREVIEW_WIDTH / 2;
        int bottomY = y + PREVIEW_TOP + PREVIEW_HEIGHT - 3;
        boolean hovered = isInside(mouseX, mouseY, x + PREVIEW_LEFT, y + PREVIEW_TOP,
                PREVIEW_WIDTH, PREVIEW_HEIGHT);
        float lookX = hovered ? centerX - mouseX : 0.0F;
        float lookY = hovered ? y + PREVIEW_TOP + PREVIEW_HEIGHT / 3.0F - mouseY : 0.0F;
        int scale = fittedScale(preview, PREVIEW_HEIGHT - 14, PREVIEW_WIDTH - 10) * 2;
        MasqueradeRenderer.renderWithoutPlayerReplacement(() ->
                InventoryScreen.drawEntity(context, centerX, bottomY, scale, lookX, lookY, preview));
    }

    private void select(MasqueradeMorph clicked) {
        MasqueradeMorph current = selectedMorph();
        pendingSelection = clicked.equals(current) ? null : clicked;
        hasPendingSelection = true;
        MasqueradeClient.select(clicked);
    }

    private MasqueradeMorph selectedMorph() {
        return hasPendingSelection ? pendingSelection : MasqueradeClientState.localActiveMorph();
    }

    private Text displayName(MasqueradeMorph morph) {
        if (morph.kind() == MasqueradeMorph.Kind.PLAYER) {
            return Text.literal(morph.playerProfile().getName());
        }
        if (!Registries.ENTITY_TYPE.containsId(morph.entityTypeId())) {
            return Text.literal(morph.entityTypeId().toString());
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(morph.entityTypeId());
        return entityType.getName();
    }

    private int fittedScale(LivingEntity entity, int availableHeight, int availableWidth) {
        float height = Math.max(0.5F, entity.getHeight());
        float width = Math.max(0.5F, entity.getWidth());
        float heightScale = availableHeight / height;
        float widthScale = availableWidth / width;
        return Math.max(2, Math.round(Math.min(heightScale, widthScale) * 0.48F));
    }

    private double maxScroll() {
        return Math.max(0.0D, filteredMorphs.size() * ROW_HEIGHT - LIST_HEIGHT);
    }

    private int scrollerThumbY(int guiY) {
        int travel = SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT;
        if (maxScroll() <= 0.0D) {
            return guiY + SCROLLER_Y;
        }
        return guiY + SCROLLER_Y + (int) Math.round(scrollAmount / maxScroll() * travel);
    }

    private void setScrollFromScroller(double mouseY, int guiY) {
        int travel = SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT;
        double thumbTop = mouseY - scrollerDragOffset - (guiY + SCROLLER_Y);
        scrollAmount = MathHelper.clamp(thumbTop / travel * maxScroll(), 0.0D, maxScroll());
    }

    private int guiX() {
        return (width - GUI_WIDTH) / 2;
    }

    private int guiY() {
        return (height - GUI_HEIGHT) / 2;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
