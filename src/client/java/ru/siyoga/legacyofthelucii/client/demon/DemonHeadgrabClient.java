package ru.siyoga.legacyofthelucii.client.demon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;
import ru.siyoga.legacyofthelucii.network.DemonHeadgrabNetwork;

public final class DemonHeadgrabClient {
    private static final Identifier BAR_BACKGROUND =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/small_slime_bar_background.png"
            );
    private static final Identifier BAR_PROGRESS =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/small_slime_bar_progress.png"
            );
    private static final Identifier BAR_COOLDOWN =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/small_slime_bar_cooldown.png"
            );
    private static final Identifier MEDIUM_BAR_BACKGROUND =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/medium_slime_bar_background.png"
            );
    private static final Identifier MEDIUM_BAR_PROGRESS =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/medium_slime_bar_progress.png"
            );
    private static final Identifier LARGE_BAR_BACKGROUND =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/big_slime_bar_background.png"
            );
    private static final Identifier LARGE_BAR_PROGRESS =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "textures/gui/hud/big_slime_bar_progress.png"
            );

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    private static boolean active;
    private static int attachedSlimeEntityId = -1;

    private static boolean qteHolding;
    private static boolean previousJumpPressed;
    private static int localHoldTicks;
    private static int failedAttemptCooldownTicks;

    private static boolean registered;

    private DemonHeadgrabClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientPlayNetworking.registerGlobalReceiver(
                DemonHeadgrabNetwork.STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean receivedActive =
                            buf.readBoolean();

                    int slimeEntityId =
                            buf.readVarInt();

                    client.execute(() ->
                            updateState(
                                    client,
                                    receivedActive,
                                    slimeEntityId
                            )
                    );
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                DemonHeadgrabNetwork.VISUAL_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean attached = buf.readBoolean();
                    int slimeEntityId = buf.readVarInt();
                    int victimEntityId = buf.readVarInt();

                    client.execute(() ->
                            DemonHeadgrabVisualState.update(
                                    attached,
                                    slimeEntityId,
                                    victimEntityId
                            )
                    );
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                DemonHeadgrabNetwork.QTE_RESULT_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean success = buf.readBoolean();
                    buf.readFloat();

                    client.execute(() -> {
                        if (!success) {
                            failedAttemptCooldownTicks =
                                    DemonHeadgrabSystem
                                            .FAILED_QTE_RETRY_TICKS;

                            qteHolding = false;
                            localHoldTicks = 0;
                        }
                    });
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(
                DemonHeadgrabClient::tick
        );

        HudRenderCallback.EVENT.register(
                DemonHeadgrabClient::renderHud
        );

        LegacyOfTheLucii.LOGGER.info(
                "Demon headgrab: compact head replacement visual and two-zone QTE bar registered."
        );
    }

    public static void reset() {
        DemonHeadgrabVisualState.clear();

        active = false;
        attachedSlimeEntityId = -1;

        qteHolding = false;
        previousJumpPressed = false;
        localHoldTicks = 0;
        failedAttemptCooldownTicks = 0;
    }

    private static void updateState(
            MinecraftClient client,
            boolean receivedActive,
            int slimeEntityId
    ) {
        active = receivedActive;
        attachedSlimeEntityId =
                receivedActive ? slimeEntityId : -1;

        qteHolding = false;
        localHoldTicks = 0;
        failedAttemptCooldownTicks = 0;

        previousJumpPressed =
                client.options.jumpKey.isPressed();
    }

    private static void tick(MinecraftClient client) {
        DemonHeadgrabVisualState.tick();

        if (failedAttemptCooldownTicks > 0) {
            failedAttemptCooldownTicks--;
        }

        if (!active
                || client.player == null
                || client.world == null) {
            qteHolding = false;
            localHoldTicks = 0;
            previousJumpPressed = false;
            failedAttemptCooldownTicks = 0;
            return;
        }

        boolean jumpPressed =
                client.options.jumpKey.isPressed();

        /*
         * After a failed release, the player must wait for the short cooldown
         * and press SPACE again. Holding it through the cooldown cannot begin
         * a new attempt.
         */
        boolean canBeginAttempt =
                failedAttemptCooldownTicks <= 0;

        if (jumpPressed
                && !previousJumpPressed
                && canBeginAttempt) {
            qteHolding = true;
            localHoldTicks = 0;
            sendQteInput(true);
        }

        if (qteHolding && jumpPressed) {
            localHoldTicks++;
        }

        if (!jumpPressed
                && previousJumpPressed
                && qteHolding) {
            sendQteInput(false);
            qteHolding = false;
        }

        previousJumpPressed = jumpPressed;
    }

    private static void sendQteInput(boolean pressed) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(pressed);

        ClientPlayNetworking.send(
                DemonHeadgrabNetwork.QTE_INPUT_PACKET,
                buf
        );
    }

    private static void renderHud(
            DrawContext context,
            float tickDelta
    ) {
        MinecraftClient client =
                MinecraftClient.getInstance();

        if (!active
                || client.player == null
                || client.options.hudHidden) {
            return;
        }

        float progress = qteHolding
                ? DemonHeadgrabSystem.calculateQteProgress(
                        localHoldTicks
                )
                : 0.0F;

        renderMountStyleBar(
                client,
                context,
                progress
        );
    }

    private static void renderMountStyleBar(
            MinecraftClient client,
            DrawContext context,
            float progress
    ) {
        int scaledWidth =
                client.getWindow().getScaledWidth();

        int scaledHeight =
                client.getWindow().getScaledHeight();

        int x = scaledWidth / 2
                - BAR_WIDTH / 2;

        int y = scaledHeight - 29;

        BarTextures textures = getBarTextures(client);

        context.drawTexture(
                textures.background,
                x,
                y,
                0,
                0,
                BAR_WIDTH,
                BAR_HEIGHT,
                BAR_WIDTH,
                BAR_HEIGHT
        );

        float clamped = MathHelper.clamp(
                progress,
                0.0F,
                1.0F
        );

        int fillWidth = Math.min(
                BAR_WIDTH,
                Math.round(
                        clamped * BAR_WIDTH
                )
        );

        if (fillWidth > 0) {
            context.drawTexture(
                    textures.progress,
                    x,
                    y,
                    0,
                    0,
                    fillWidth,
                    BAR_HEIGHT,
                    BAR_WIDTH,
                    BAR_HEIGHT
            );
        }

        if (failedAttemptCooldownTicks > 0) {
            int cooldownWidth = Math.min(
                    BAR_WIDTH,
                    Math.round(
                            BAR_WIDTH
                                    * failedAttemptCooldownTicks
                                    / (float) DemonHeadgrabSystem
                                            .FAILED_QTE_RETRY_TICKS
                    )
            );

            context.drawTexture(
                    BAR_COOLDOWN,
                    x,
                    y,
                    0,
                    0,
                    cooldownWidth,
                    BAR_HEIGHT,
                    BAR_WIDTH,
                    BAR_HEIGHT
            );
        }
    }

    private static BarTextures getBarTextures(MinecraftClient client) {
        if (client.world != null
                && client.world.getEntityById(attachedSlimeEntityId)
                instanceof SlimeEntity slime) {
            if (slime.getSize()
                    == DemonHeadgrabSystem.MEDIUM_SLIME_SIZE) {
                return new BarTextures(
                        MEDIUM_BAR_BACKGROUND,
                        MEDIUM_BAR_PROGRESS
                );
            }

            if (slime.getSize()
                    > DemonHeadgrabSystem.MEDIUM_SLIME_SIZE) {
                return new BarTextures(
                        LARGE_BAR_BACKGROUND,
                        LARGE_BAR_PROGRESS
                );
            }
        }

        return new BarTextures(BAR_BACKGROUND, BAR_PROGRESS);
    }

    private record BarTextures(
            Identifier background,
            Identifier progress
    ) {
    }
}
