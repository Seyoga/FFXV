package ru.siyoga.legacyofthelucii.client.royalarms;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.network.RoyalArmsGuardNetwork;

public final class RoyalArmsWallClient {
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static KeyBinding wallKey;
    private static KeyBinding warpKey;
    private static KeyBinding bindKey;
    private static KeyBinding guardKey;
    private static KeyBinding ardynShadowStepKey;
    private static boolean wallKeyWasDown;
    private static boolean warpKeyWasDown;
    private static boolean bindKeyWasDown;
    private static boolean guardKeyWasDown;
    private static boolean shadowStepWasDown;

    private RoyalArmsWallClient() {
    }

    public static void register() {
        wallKey = registerKey("key.legacyofthelucii.royal_arms.wall", GLFW.GLFW_KEY_1);
        warpKey = registerKey("key.legacyofthelucii.royal_arms.warp", GLFW.GLFW_KEY_2);
        bindKey = registerKey("key.legacyofthelucii.royal_arms.bind", GLFW.GLFW_KEY_3);
        guardKey = registerKey("key.legacyofthelucii.royal_arms.guard", GLFW.GLFW_KEY_4);
        ardynShadowStepKey = registerKey("key.legacyofthelucii.royal_arms.ardyn_shadow_step", GLFW.GLFW_KEY_Z);
        ClientTickEvents.END_CLIENT_TICK.register(RoyalArmsWallClient::tick);
    }

    private static KeyBinding registerKey(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                defaultKey,
                CATEGORY
        ));
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.currentScreen != null) {
            wallKeyWasDown = false;
            warpKeyWasDown = false;
            bindKeyWasDown = false;
            guardKeyWasDown = false;
            if (shadowStepWasDown) {
                sendShadowStep(false);
                shadowStepWasDown = false;
            }
            return;
        }

        long handle = client.getWindow().getHandle();
        boolean controlDown = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean numberOneDown = isPressed(wallKey);
        boolean numberTwoDown = isPressed(warpKey);
        boolean numberThreeDown = isPressed(bindKey);
        boolean numberFourDown = isPressed(guardKey);
        boolean wallKeyDown = controlDown && numberOneDown;
        boolean warpKeyDown = controlDown && numberTwoDown;
        boolean bindKeyDown = controlDown && numberThreeDown;
        boolean guardKeyDown = controlDown && numberFourDown;
        boolean shadowStepDown = isPressed(ardynShadowStepKey);

        if (wallKeyDown && !wallKeyWasDown && ClientLuciiState.legacy() == LuciiLegacy.NOCTIS && ClientLuciiState.royalArmsActive()) {
            ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_WALL_PACKET, PacketByteBufs.empty());
        }

        boolean canWarp = (ClientLuciiState.legacy() == LuciiLegacy.NOCTIS || ClientLuciiState.legacy() == LuciiLegacy.ARDYN)
                && ClientLuciiState.royalArmsActive();
        if (warpKeyDown && !warpKeyWasDown && canWarp) {
            ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_WARP_PACKET, PacketByteBufs.empty());
        }

        boolean canBind = (ClientLuciiState.legacy() == LuciiLegacy.NOCTIS || ClientLuciiState.legacy() == LuciiLegacy.ARDYN)
                && ClientLuciiState.royalArmsActive();
        if (canBind && bindKeyDown && !bindKeyWasDown) {
            sendBindToggle();
        } else if (!canBind && bindKeyWasDown) {
            bindKeyDown = false;
        }

        boolean canGuard = ClientLuciiState.legacy() == LuciiLegacy.NOCTIS
                && ClientLuciiState.royalArmsActive();
        if (canGuard && guardKeyDown && !guardKeyWasDown) {
            sendGuardToggle(!RoyalArmsGuardClient.isActive());
        } else if (!canGuard && RoyalArmsGuardClient.isActive()) {
            sendGuardToggle(false);
        }

        if (ClientLuciiState.legacy() == LuciiLegacy.ARDYN && ClientLuciiState.royalArmsActive() && shadowStepDown != shadowStepWasDown) {
            sendShadowStep(shadowStepDown);
        } else if ((ClientLuciiState.legacy() != LuciiLegacy.ARDYN || !ClientLuciiState.royalArmsActive()) && shadowStepWasDown) {
            sendShadowStep(false);
            shadowStepDown = false;
        }

        wallKeyWasDown = wallKeyDown;
        warpKeyWasDown = warpKeyDown;
        bindKeyWasDown = bindKeyDown;
        guardKeyWasDown = guardKeyDown;
        shadowStepWasDown = shadowStepDown;
    }

    private static boolean isPressed(KeyBinding keyBinding) {
        return keyBinding != null && keyBinding.isPressed();
    }

    private static void sendShadowStep(boolean active) {
        var buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ClientPlayNetworking.send(LuciiNetwork.ARDYN_SHADOW_STEP_PACKET, buf);
    }

    public static void sendBindConfirm() {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(LuciiNetwork.ROYAL_ARMS_BIND_CONFIRM_ACTION);
        ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_BIND_PACKET, buf);
    }

    private static void sendBindToggle() {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(LuciiNetwork.ROYAL_ARMS_BIND_TOGGLE_ACTION);
        ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_BIND_PACKET, buf);
    }

    private static void sendGuardToggle(boolean active) {
        var buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ClientPlayNetworking.send(RoyalArmsGuardNetwork.TOGGLE_PACKET, buf);
    }
}
