package ru.siyoga.legacyofthelucii.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

/** Restores the Ctrl+5 development shortcut that sends the existing server debug packet. */
public final class DemonizationDebugClient {
    private static boolean registered;
    private static boolean wasPressed;

    private DemonizationDebugClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(DemonizationDebugClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }

        long window = client.getWindow().getHandle();
        boolean controlDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean fiveDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_5)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_KP_5);
        boolean pressed = controlDown && fiveDown;

        if (pressed
                && !wasPressed
                && client.currentScreen == null
                && client.player != null
                && client.getNetworkHandler() != null) {
            LegacyOfTheLucii.LOGGER.info("Demonization debug: Ctrl+5 packet sent by client.");
            ClientPlayNetworking.send(LuciiNetwork.DEBUG_DEMONIZE_PACKET, PacketByteBufs.create());
        }

        wasPressed = pressed;
    }
}
