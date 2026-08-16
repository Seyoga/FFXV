package ru.siyoga.legacyofthelucii.client.gui.masquerade;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeClient;
import ru.siyoga.legacyofthelucii.client.masquerade.MasqueradeClientState;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class MasqueradeKeybindings {
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static final double TARGET_SELECT_RANGE = 10.0D;
    private static final double TARGET_RAYCAST_RANGE = 48.0D;
    private static KeyBinding openKey;

    private MasqueradeKeybindings() {
    }

    public static void register() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.masquerade.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(MasqueradeKeybindings::tick);
    }

    private static void tick(MinecraftClient client) {
        while (openKey.wasPressed()) {
            if (isShiftDown(client)) {
                selectTarget(client);
            } else {
                MasqueradeScreen.open(client);
            }
        }
    }

    private static void selectTarget(MinecraftClient client) {
        if (client.player == null || ClientLuciiState.legacy() != LuciiLegacy.ARDYN) {
            return;
        }
        EntityHitResult hit = findTargetedEntity(client);
        if (hit != null && isEligibleTarget(client.player, hit.getEntity())) {
            LivingEntity target = (LivingEntity) hit.getEntity();
            if (client.player.squaredDistanceTo(target) > TARGET_SELECT_RANGE * TARGET_SELECT_RANGE) {
                client.player.sendMessage(
                        net.minecraft.text.Text.translatable("message.legacyofthelucii.masquerade.target_too_far"),
                        true
                );
                return;
            }
            MasqueradeClient.selectTarget(target.getUuid());
            return;
        }
        if (MasqueradeClientState.localTargetUuid() != null) {
            MasqueradeClient.clearTarget();
            return;
        }
        client.player.sendMessage(
                net.minecraft.text.Text.translatable("message.legacyofthelucii.masquerade.target_player"),
                true
        );
    }

    private static EntityHitResult findTargetedEntity(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof EntityHitResult directHit
                && isEligibleTarget(client.player, directHit.getEntity())) {
            return directHit;
        }

        Vec3d start = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        Vec3d end = start.add(look.multiply(TARGET_RAYCAST_RANGE));
        Box searchBox = client.player.getBoundingBox()
                .stretch(look.multiply(TARGET_RAYCAST_RANGE))
                .expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                client.player,
                start,
                end,
                searchBox,
                entity -> isEligibleTarget(client.player, entity),
                TARGET_RAYCAST_RANGE * TARGET_RAYCAST_RANGE
        );
        if (entityHit == null) {
            return null;
        }

        HitResult blockHit = client.world.raycast(new net.minecraft.world.RaycastContext(
                start,
                end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return blockHit.getType() == HitResult.Type.MISS
                || start.squaredDistanceTo(entityHit.getPos()) <= start.squaredDistanceTo(blockHit.getPos())
                ? entityHit
                : null;
    }

    private static boolean isEligibleTarget(PlayerEntity player, Entity entity) {
        return entity instanceof LivingEntity
                && entity != player
                && (entity instanceof PlayerEntity || entity instanceof MobEntity);
    }

    private static boolean isShiftDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
