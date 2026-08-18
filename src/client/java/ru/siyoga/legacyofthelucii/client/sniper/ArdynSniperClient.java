package ru.siyoga.legacyofthelucii.client.sniper;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperNetwork;

public final class ArdynSniperClient {
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static final Identifier HUD_TEXTURE =
            new Identifier(LegacyOfTheLucii.MOD_ID, "textures/gui/scope_hud.png");
    private static final int HUD_TEXTURE_SIZE = 512;
    private static final float HUD_SCALE = 0.72F;
    private static final int OUTSIDE_DIM_COLOR = 0x38000000;
    private static final float BLUR_SAMPLE_ALPHA = 0.12F;
    private static final float[][] BLUR_OFFSETS = {
            {-3.0F, 0.0F},
            {3.0F, 0.0F},
            {0.0F, -3.0F},
            {0.0F, 3.0F},
            {-3.0F, -3.0F},
            {3.0F, -3.0F},
            {-3.0F, 3.0F},
            {3.0F, 3.0F},
            {-7.0F, 0.0F},
            {7.0F, 0.0F},
            {0.0F, -7.0F},
            {0.0F, 7.0F},
            {-6.0F, -6.0F},
            {6.0F, -6.0F},
            {-6.0F, 6.0F},
            {6.0F, 6.0F},
            {-10.0F, 0.0F},
            {10.0F, 0.0F},
            {0.0F, -10.0F},
            {0.0F, 10.0F}
    };

    private static KeyBinding sniperKey;
    private static boolean active;
    private static boolean comboWasDown;
    private static int localCooldownTicks;
    private static int sceneTextureId = -1;
    private static int sceneTextureWidth;
    private static int sceneTextureHeight;

    private ArdynSniperClient() {
    }

    public static void register() {
        sniperKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.ardyn_sniper",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_6,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(ArdynSniperClient::tick);
        ClientPreAttackCallback.EVENT.register(ArdynSniperClient::onPreAttack);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                active ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (active && player.getStackInHand(hand).getItem() instanceof BlockItem) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    public static void setState(java.util.UUID ownerUuid, boolean newActive, int cooldownTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || ownerUuid == null || !ownerUuid.equals(client.player.getUuid())) {
            return;
        }
        ArdynSniperAnimations.onLocalState(ownerUuid, newActive);
        setState(newActive, cooldownTicks);
    }

    public static void setState(boolean newActive, int cooldownTicks) {
        active = newActive;
        localCooldownTicks = Math.max(0, cooldownTicks);
        if (newActive) {
            forceFirstPerson(MinecraftClient.getInstance());
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void clear() {
        active = false;
        comboWasDown = false;
        localCooldownTicks = 0;
        releaseSceneTexture();
    }

    public static void renderScope(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || client.player == null || client.world == null) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int hudSize = Math.max(1, Math.round(Math.min(screenWidth, screenHeight) * HUD_SCALE));
        int x = (screenWidth - hudSize) / 2;
        int y = (screenHeight - hudSize) / 2;

        renderOutsideBlur(context, client, x, y, hudSize, screenWidth, screenHeight);
        context.drawTexture(
                HUD_TEXTURE,
                x,
                y,
                hudSize,
                hudSize,
                0.0F,
                0.0F,
                HUD_TEXTURE_SIZE,
                HUD_TEXTURE_SIZE,
                HUD_TEXTURE_SIZE,
                HUD_TEXTURE_SIZE
        );
    }

    private static void tick(MinecraftClient client) {
        if (client.isPaused()) {
            return;
        }

        if (localCooldownTicks > 0) {
            localCooldownTicks--;
        }

        if (active) {
            forceFirstPerson(client);
        }

        boolean comboDown = isCtrlDown(client) && isSixDown(client);
        if (comboDown && !comboWasDown && client.getNetworkHandler() != null) {
            if (!active) {
                sendAction(ArdynSniperNetwork.TOGGLE_ACTION);
            } else if (client.player != null
                    && ArdynSniperAnimations.canLocalUnequip(client.player.getUuid())) {
                ArdynSniperAnimations.predictLocalUnequip(client.player.getUuid());
                active = false;
                releaseSceneTexture();
                sendAction(ArdynSniperNetwork.TOGGLE_ACTION);
            }
        }
        comboWasDown = comboDown;

        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            active = false;
            releaseSceneTexture();
        }
    }

    private static boolean onPreAttack(
            MinecraftClient client,
            net.minecraft.client.network.ClientPlayerEntity player,
            int clickCount
    ) {
        if (!active) {
            return false;
        }
        if (clickCount == 1
                && localCooldownTicks <= 0
                && client.getNetworkHandler() != null
                && ArdynSniperAnimations.canLocalShoot(player.getUuid())) {
            sendAction(ArdynSniperNetwork.SHOOT_ACTION);
        }
        return clickCount > 0;
    }

    private static void renderOutsideBlur(
            DrawContext context,
            MinecraftClient client,
            int scopeX,
            int scopeY,
            int scopeSize,
            int screenWidth,
            int screenHeight
    ) {
        int framebufferWidth = client.getWindow().getFramebufferWidth();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }

        captureScene(client, framebufferWidth, framebufferHeight);
        if (sceneTextureId < 0) {
            return;
        }

        drawBlurRegion(context, 0, 0, screenWidth, scopeY, screenWidth, screenHeight, framebufferWidth, framebufferHeight);
        drawBlurRegion(context, 0, scopeY + scopeSize, screenWidth, screenHeight, screenWidth, screenHeight, framebufferWidth, framebufferHeight);
        drawBlurRegion(context, 0, scopeY, scopeX, scopeY + scopeSize, screenWidth, screenHeight, framebufferWidth, framebufferHeight);
        drawBlurRegion(context, scopeX + scopeSize, scopeY, screenWidth, scopeY + scopeSize, screenWidth, screenHeight, framebufferWidth, framebufferHeight);

        context.fill(0, 0, screenWidth, scopeY, OUTSIDE_DIM_COLOR);
        context.fill(0, scopeY + scopeSize, screenWidth, screenHeight, OUTSIDE_DIM_COLOR);
        context.fill(0, scopeY, scopeX, scopeY + scopeSize, OUTSIDE_DIM_COLOR);
        context.fill(scopeX + scopeSize, scopeY, screenWidth, scopeY + scopeSize, OUTSIDE_DIM_COLOR);
    }

    private static void captureScene(MinecraftClient client, int width, int height) {
        ensureSceneTexture(width, height);
        client.getFramebuffer().beginWrite(false);
        RenderSystem.bindTexture(sceneTextureId);
        GL11.glCopyTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                0,
                width,
                height
        );
    }

    private static void ensureSceneTexture(int width, int height) {
        if (sceneTextureId >= 0 && sceneTextureWidth == width && sceneTextureHeight == height) {
            return;
        }
        releaseSceneTexture();
        sceneTextureId = TextureUtil.generateTextureId();
        sceneTextureWidth = width;
        sceneTextureHeight = height;
        TextureUtil.prepareImage(sceneTextureId, width, height);
        RenderSystem.bindTexture(sceneTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void releaseSceneTexture() {
        if (sceneTextureId >= 0) {
            int texture = sceneTextureId;
            sceneTextureId = -1;
            sceneTextureWidth = 0;
            sceneTextureHeight = 0;
            if (RenderSystem.isOnRenderThread()) {
                TextureUtil.releaseTextureId(texture);
            } else {
                RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(texture));
            }
        }
    }

    private static void drawBlurRegion(
            DrawContext context,
            int left,
            int top,
            int right,
            int bottom,
            int screenWidth,
            int screenHeight,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (right <= left || bottom <= top) {
            return;
        }

        context.enableScissor(left, top, right, bottom);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, sceneTextureId);

        for (float[] offset : BLUR_OFFSETS) {
            drawSceneSample(
                    context,
                    screenWidth,
                    screenHeight,
                    offset[0] / framebufferWidth,
                    offset[1] / framebufferHeight,
                    BLUR_SAMPLE_ALPHA
            );
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        context.disableScissor();
    }

    private static void drawSceneSample(
            DrawContext context,
            int screenWidth,
            int screenHeight,
            float uOffset,
            float vOffset,
            float alpha
    ) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(matrix, 0.0F, screenHeight, 0.0F).texture(clampUv(0.0F + uOffset), clampUv(0.0F + vOffset)).next();
        buffer.vertex(matrix, screenWidth, screenHeight, 0.0F).texture(clampUv(1.0F + uOffset), clampUv(0.0F + vOffset)).next();
        buffer.vertex(matrix, screenWidth, 0.0F, 0.0F).texture(clampUv(1.0F + uOffset), clampUv(1.0F + vOffset)).next();
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).texture(clampUv(0.0F + uOffset), clampUv(1.0F + vOffset)).next();
        Tessellator.getInstance().draw();
    }

    private static float clampUv(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void forceFirstPerson(MinecraftClient client) {
        if (client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    private static void sendAction(int action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(action);
        ClientPlayNetworking.send(ArdynSniperNetwork.ACTION_PACKET, buf);
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isSixDown(MinecraftClient client) {
        if (sniperKey != null && sniperKey.isPressed()) {
            return true;
        }
        return client.getWindow() != null
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_6);
    }
}
