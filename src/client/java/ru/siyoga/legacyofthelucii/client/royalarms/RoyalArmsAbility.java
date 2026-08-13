package ru.siyoga.legacyofthelucii.client.royalarms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.royalarms.bind.RoyalArmsBindClient;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.RoyalArmsInventoryFilter;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

public final class RoyalArmsAbility {
    private static final double RADIUS = 2.5D;
    private static final double INNER_RADIUS = 1.45D;
    private static final double ORBIT_Y_OFFSET = 1.0D;
    private static final double BOB_HEIGHT = 0.3D;
    private static final float ITEM_BASE_SCALE = 1.0F;
    private static final float TARGET_ITEM_SCALE = 1.48F;
    private static final float NORMAL_ORBIT_SPEED = 2.0F;
    private static final float FAST_ORBIT_SPEED = 5.0F;
    private static final float ARDYN_INNER_RING_SPEED_MULTIPLIER = 1.65F;
    private static final float ITEM_SPIN_SPEED = 3.0F;
    private static final int APPEAR_TICKS = 24;
    private static final int DISAPPEAR_TICKS = 24;
    private static final int INVENTORY_REFRESH_TICKS = 10;
    private static final int ATTACK_EQUIP_COOLDOWN_TICKS = 5;
    // BEGIN PHANTOM_GUARD_THREE_LAYER_V7 CONSTANTS
    private static final int GUARD_LAYER_UPPER = 0;
    private static final int GUARD_LAYER_MIDDLE = 1;
    private static final int GUARD_LAYER_LOWER = 2;
    private static final int GUARD_LAYER_COUNT = 3;
    private static final int GUARD_MAX_APPROACH_TICKS = 6;
    private static final float GUARD_FORMATION_LERP = 0.16F;
    private static final double GUARD_UPPER_Y_OFFSET = 1.62D;
    private static final double GUARD_MIDDLE_Y_OFFSET = 1.02D;
    private static final double GUARD_LOWER_Y_OFFSET = 0.42D;
    private static final double GUARD_BOB_HEIGHT = 0.06D;
    private static final int EXPLOSION_GUARD_HOLD_TICKS = 8;
    private static final int EXPLOSION_GUARD_SPIN_TICKS = 52;
    private static final float EXPLOSION_GUARD_BASE_EXTRA_SPEED = 18.0F;
    private static final float EXPLOSION_GUARD_EXTRA_SPEED_PER_ITEM = 1.8F;
    private static final float EXPLOSION_GUARD_MAX_EXTRA_SPEED = 31.0F;
    // END PHANTOM_GUARD_THREE_LAYER_V7 CONSTANTS
    private static final String CATEGORY = "key.categories.legacyofthelucii";

    private static KeyBinding toggleKey;
    private static KeyBinding filterKey;

    private static final List<FloatingItem> floatingItems = new ArrayList<>();
    private static final Map<UUID, RemoteRoyalArmsVisual> remoteVisuals = new HashMap<>();
    private static final List<UUID> ardynBarrageOwners = new ArrayList<>();
    private static final Map<OwnerLayerKey, GuardOrbitBoost> guardLayerBoosts = new HashMap<>();
    private static final Map<UUID, float[]> guardLayerOffsets = new HashMap<>();
    private static final Map<UUID, float[]> previousGuardLayerOffsets = new HashMap<>();
    private static final Map<UUID, ExplosionGuardOrbit> explosionGuardOrbits = new HashMap<>();
    private static final Map<UUID, Float> remoteExplosionOrbitOffsets = new HashMap<>();
    private static final Map<UUID, Float> previousRemoteExplosionOrbitOffsets = new HashMap<>();
    private static final Map<UUID, Float> remoteGuardFormationProgress = new HashMap<>();
    private static final Map<UUID, Float> previousRemoteGuardFormationProgress = new HashMap<>();
    private static float localGuardFormationProgress;
    private static float previousLocalGuardFormationProgress;
    private static boolean lastActive;
    private static RoyalArmsInventoryFilter currentFilter = RoyalArmsInventoryFilter.ALL;
    private static Vec3d lastPlayerPos;
    private static Vec3d currentPlayerPos;
    private static int refreshTimer;
    private static int lastSelectedSlot = -1;
    private static int lastTargetIndex = -1;
    private static FloatingItem targetedItem;
    private static int attackEquipCooldown;
    private static int toggleLockTicks;
    private static float previousOrbitTime;
    private static float orbitTime;
    private static float previousItemSpinAngle;
    private static float itemSpinAngle;
    private static boolean initialAuraAppearance;
    private static boolean wasSneaking;
    private static boolean orbitPausedByDoubleSneak;
    private static int sneakDoubleTapTicks;

    private RoyalArmsAbility() {
    }

    public static void register() {
        toggleKey = registerKey("key.legacyofthelucii.royal_arms.toggle", GLFW.GLFW_KEY_R);
        filterKey = registerKey("key.legacyofthelucii.royal_arms.filter", GLFW.GLFW_KEY_H);

        ClientTickEvents.END_CLIENT_TICK.register(RoyalArmsAbility::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(RoyalArmsAbility::render);
        ClientPreAttackCallback.EVENT.register(RoyalArmsAbility::onPreAttack);
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
        // END_CLIENT_TICK is still invoked while an integrated singleplayer world is
        // paused. Freeze the whole visual state so rendering resumes from the exact
        // same interpolation endpoints. In multiplayer, opening a screen does not
        // make MinecraftClient report itself as paused, so the orbit keeps updating.
        if (client.isPaused()) {
            return;
        }

        if (toggleLockTicks > 0) {
            toggleLockTicks--;
        }

        while (toggleKey.wasPressed()) {
            if (toggleLockTicks > 0) {
                continue;
            }

            sendRoyalArmsToggle(!ClientLuciiState.royalArmsActive());
            toggleLockTicks = toggleLockDuration(!ClientLuciiState.royalArmsActive());
        }

        while (filterKey.wasPressed()) {
            if (!ClientLuciiState.royalArmsActive()) {
                continue;
            }

            currentFilter = currentFilter.next();
            rebuildAura(client);
            sendRoyalArmsFilter(currentFilter);
            sendActionbar(client, "Royal Arms filter: " + currentFilter.displayName());
        }

        boolean active = ClientLuciiState.royalArmsActive();
        if (active != lastActive) {
            if (active) {
                clearAura();
                rebuildAura(client);
                initialAuraAppearance = true;
            } else {
                startClosingAura();
                if (client.player != null) {
                    clearGuardCombatEffects(client.player.getUuid());
                }
            }
            toggleLockTicks = Math.max(toggleLockTicks, toggleLockDuration(active));
            lastActive = active;
        }

        tickClosingAura();
        tickRemoteVisuals();
        tickGuardFormationProgress(client);
        advanceGuardLayerBoosts();
        tickRemoteExplosionEffects(client.player == null ? null : client.player.getUuid());

        if (client.player != null && client.world != null && (active || hasClosingLocalItems())) {
            updateLocalPlayerPosition(client);
        }

        if (!active || client.player == null || client.world == null) {
            return;
        }

        updateOrbitSpeedState(client);
        previousOrbitTime = orbitTime;
        previousItemSpinAngle = itemSpinAngle;
        UUID localOwnerUuid = client.player.getUuid();
        float normalOrbitSpeed = currentOrbitSpeed(client);
        boolean explosionActive = explosionGuardOrbits.containsKey(localOwnerUuid);
        float orbitDelta = advanceLocalExplosionGuardOrbit(localOwnerUuid, normalOrbitSpeed);
        float layerMotion = maxGuardLayerMotion(localOwnerUuid);

        if (!initialAuraAppearance || explosionActive) {
            orbitTime += orbitDelta;
        }

        float visualMotion = Math.max(Math.abs(orbitDelta), layerMotion);
        if (visualMotion > 0.001F) {
            float spinMultiplier = MathHelper.clamp(
                    visualMotion / Math.max(1.0F, normalOrbitSpeed),
                    1.0F,
                    5.0F
            );
            itemSpinAngle = (itemSpinAngle + ITEM_SPIN_SPEED * spinMultiplier) % 360.0F;
        }

        if (attackEquipCooldown > 0) {
            attackEquipCooldown--;
        }

        int selectedSlot = client.player.getInventory().selectedSlot;
        if (selectedSlot != lastSelectedSlot) {
            refreshTimer = 0;
            rebuildAura(client);
        }

        refreshTimer++;
        if (refreshTimer >= INVENTORY_REFRESH_TICKS) {
            refreshTimer = 0;
            rebuildAura(client);
        }

        float time = orbitTime;
        int charges = ClientLuciiState.ardynWarpCharges();
        for (FloatingItem item : floatingItems) {
            if (item.closing) {
                continue;
            }

            item.innerTarget = shouldUseArdynInnerRing(ClientLuciiState.legacy(), charges, item.index, floatingItems.size());
            item.innerProgress = MathHelper.lerp(0.12F, item.innerProgress, item.innerTarget ? 1.0F : 0.0F);
            float normalTargetAngle = getRingAngleDegrees(
                    ClientLuciiState.legacy(),
                    charges,
                    item.index,
                    floatingItems.size()
            );
            float guardTargetAngle = getGuardRingAngleDegrees(item.index, floatingItems.size());
            item.targetAngle = lerpAngleDegrees(
                    normalTargetAngle,
                    guardTargetAngle,
                    localGuardFormationProgress
            );
            float diff = MathHelper.wrapDegrees(item.targetAngle - item.angle);
            item.angle += diff * 0.18F;
            item.highlightScale = MathHelper.lerp(0.16F, item.highlightScale, item == targetedItem ? TARGET_ITEM_SCALE : 1.0F);
            item.spawnTicks = Math.min(APPEAR_TICKS, item.spawnTicks + 1);
        }
        if (initialAuraAppearance
                && floatingItems.stream().noneMatch(FloatingItem::isAppearing)) {
            initialAuraAppearance = false;
        }

        FloatingItem newTarget = findTargetedItem(client, time);
        int newTargetIndex = newTarget == null ? -1 : newTarget.index;
        if (newTargetIndex != lastTargetIndex) {
            targetedItem = newTarget;
            lastTargetIndex = newTargetIndex;
        }

    }

    private static void updateOrbitSpeedState(MinecraftClient client) {
        boolean sneaking = client.player != null && client.player.isSneaking();
        if (sneaking && !wasSneaking) {
            if (sneakDoubleTapTicks > 0) {
                orbitPausedByDoubleSneak = true;
                sneakDoubleTapTicks = 0;
            } else {
                sneakDoubleTapTicks = 8;
            }
        }

        if (!sneaking && wasSneaking) {
            orbitPausedByDoubleSneak = false;
        }

        if (sneakDoubleTapTicks > 0) {
            sneakDoubleTapTicks--;
        }

        wasSneaking = sneaking;
    }

    private static float currentOrbitSpeed(MinecraftClient client) {
        if (client.player == null) {
            return FAST_ORBIT_SPEED;
        }

        if (orbitPausedByDoubleSneak && client.player.isSneaking()) {
            return 0.0F;
        }

        return client.player.isSneaking() ? NORMAL_ORBIT_SPEED : FAST_ORBIT_SPEED;
    }

    private static float remoteOrbitSpeed(AbstractClientPlayerEntity player) {
        return player.isSneaking() ? NORMAL_ORBIT_SPEED : FAST_ORBIT_SPEED;
    }

    private static boolean onPreAttack(MinecraftClient client, net.minecraft.client.network.ClientPlayerEntity player, int clickCount) {
        if (clickCount > 0 && RoyalArmsBindClient.isAiming(player.getUuid())) {
            RoyalArmsWallClient.sendBindConfirm();
            return true;
        }

        if (!ClientLuciiState.royalArmsActive() || clickCount == 0 || attackEquipCooldown > 0 || !player.isSneaking()) {
            return false;
        }

        FloatingItem lookedAtItem = client.world == null ? null : findTargetedItem(client, orbitTime);
        if (lookedAtItem == null) {
            sendActionbar(client, "Royal Arms: no targeted item");
            return true;
        }

        targetedItem = lookedAtItem;
        lastTargetIndex = lookedAtItem.index;
        equipTargetedItem(client);
        attackEquipCooldown = ATTACK_EQUIP_COOLDOWN_TICKS;
        return true;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = client.isPaused() ? 0.0F : context.tickDelta();
        float time = MathHelper.lerp(tickDelta, previousOrbitTime, orbitTime);
        float spinAngle = lerpAngleDegrees(previousItemSpinAngle, itemSpinAngle, tickDelta);
        float spinTime = client.world.getTime() + tickDelta;
        float localGuardProgress = MathHelper.lerp(
                tickDelta,
                previousLocalGuardFormationProgress,
                localGuardFormationProgress
        );

        if (!floatingItems.isEmpty()
                && lastPlayerPos != null
                && currentPlayerPos != null
                && !RoyalArmsBindClient.isBinding(client.player.getUuid())
                && !ardynBarrageOwners.contains(client.player.getUuid())) {
            Vec3d playerPos = lastPlayerPos.lerp(currentPlayerPos, tickDelta);
            UUID ownerUuid = client.player.getUuid();
            int total = floatingItems.size();
            for (FloatingItem item : floatingItems) {
                int layer = guardLayerForIndex(item.index);
                float itemTime = time + interpolatedGuardLayerOffset(
                        ownerUuid,
                        layer,
                        tickDelta
                ) * localGuardProgress;
                Vec3d itemPos = getItemPosition(
                        item,
                        playerPos,
                        itemTime,
                        tickDelta,
                        ClientLuciiState.legacy(),
                        ClientLuciiState.ardynWarpCharges(),
                        total,
                        localGuardProgress
                );
                renderFloatingItem(
                        context,
                        item,
                        itemPos,
                        cameraPos,
                        playerPos,
                        spinAngle,
                        tickDelta
                );
            }
        }

        renderRemoteVisuals(context, client, cameraPos, spinTime, tickDelta);
    }

    private static void renderFloatingItem(
            WorldRenderContext context,
            FloatingItem item,
            Vec3d itemPos,
            Vec3d cameraPos,
            Vec3d playerPos,
            float spinAngle,
            float tickDelta
    ) {
        boolean targeted = item == targetedItem;
        float scale = ITEM_BASE_SCALE * item.visualScale(tickDelta) * item.highlightScale;
        double dx = itemPos.x - playerPos.x;
        double dz = itemPos.z - playerPos.z;
        float lookAngle = item.closing ? item.closeLookAngle : (float) Math.toDegrees(Math.atan2(dx, dz));
        float spin = item.closing ? item.closeSpinAngle : (spinAngle + item.spinPhase) % 360.0F;

        LegacyPalette palette = LegacyPalette.forLegacy(ClientLuciiState.legacy());
        RenderTint tint = targeted ? palette.targetTint : palette.baseTint;
        renderItemPass(context, item.stack, item.seed, itemPos, cameraPos, lookAngle, spin, scale, tint);
    }

    private static void renderItemPass(
            WorldRenderContext context,
            ItemStack stack,
            int seed,
            Vec3d itemPos,
            Vec3d cameraPos,
            float lookAngle,
            float spin,
            float scale,
            RenderTint tint
    ) {
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = new TintedItemVertexConsumerProvider(context.consumers(), tint);

        matrices.push();
        matrices.translate(itemPos.x - cameraPos.x, itemPos.y - cameraPos.y, itemPos.z - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(lookAngle + 180.0F + spin));
        matrices.scale(scale, scale, scale);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.GROUND,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                consumers,
                context.world(),
                seed
        );
        matrices.pop();
    }

    private static void renderRemoteVisuals(
            WorldRenderContext context,
            MinecraftClient client,
            Vec3d cameraPos,
            float spinTime,
            float tickDelta
    ) {
        UUID selfUuid = client.player == null ? null : client.player.getUuid();
        for (Map.Entry<UUID, RemoteRoyalArmsVisual> entry : new ArrayList<>(remoteVisuals.entrySet())) {
            if (entry.getKey().equals(selfUuid)) {
                continue;
            }

            if (RoyalArmsBindClient.isBinding(entry.getKey())) {
                continue;
            }

            if (ardynBarrageOwners.contains(entry.getKey())) {
                continue;
            }

            AbstractClientPlayerEntity owner = findPlayer(client, entry.getKey());
            RemoteRoyalArmsVisual visual = entry.getValue();
            if (owner == null || visual.items.isEmpty()) {
                continue;
            }

            Vec3d playerPos = getInterpolatedPlayerPos(owner, tickDelta);
            UUID ownerUuid = entry.getKey();
            float baseTime = spinTime * remoteOrbitSpeed(owner)
                    + interpolatedRemoteExplosionOrbitOffset(ownerUuid, tickDelta);
            float guardProgress = interpolatedRemoteGuardFormationProgress(
                    ownerUuid,
                    tickDelta
            );
            int total = visual.items.size();
            LegacyPalette palette = LegacyPalette.forLegacy(visual.legacy);
            for (int i = 0; i < total; i++) {
                int index = i + 1;
                int layer = guardLayerForIndex(index);
                float itemTime = baseTime + interpolatedGuardLayerOffset(
                        ownerUuid,
                        layer,
                        tickDelta
                ) * guardProgress;
                ItemStack stack = visual.items.get(i).stack;
                RemoteFloatingItem item = visual.items.get(i);
                item.innerTarget = shouldUseArdynInnerRing(visual.legacy, visual.ardynWarpCharges, index, total);
                item.innerProgress = MathHelper.lerp(0.12F, item.innerProgress, item.innerTarget ? 1.0F : 0.0F);
                Vec3d itemPos = getRemoteItemPosition(
                        item,
                        index,
                        playerPos,
                        itemTime,
                        tickDelta,
                        visual.legacy,
                        visual.ardynWarpCharges,
                        total,
                        guardProgress
                );
                double dx = itemPos.x - playerPos.x;
                double dz = itemPos.z - playerPos.z;
                float lookAngle = item.closeTicks > 0 ? item.closeLookAngle : (float) Math.toDegrees(Math.atan2(dx, dz));
                float spinPhase = spinPhaseFor(stack);
                float spin = item.closeTicks > 0 ? item.closeSpinAngle : item.isTransitioning() ? spinPhase : (spinTime * ITEM_SPIN_SPEED + spinPhase) % 360.0F;
                item.lastRenderedLookAngle = lookAngle;
                item.lastRenderedSpinAngle = spin;
                int seed = (i + 1) * 31 + Registries.ITEM.getId(stack.getItem()).hashCode();
                renderItemPass(context, stack, seed, itemPos, cameraPos, lookAngle, spin, ITEM_BASE_SCALE * item.visualScale(tickDelta), palette.baseTint);
            }
        }
    }

    private static Vec3d getInterpolatedPlayerPos(AbstractClientPlayerEntity player, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, player.prevX, player.getX()),
                MathHelper.lerp(tickDelta, player.prevY, player.getY()),
                MathHelper.lerp(tickDelta, player.prevZ, player.getZ())
        );
    }

    private static void rebuildAura(MinecraftClient client) {
        if (!ClientLuciiState.royalArmsActive() || client.player == null) {
            return;
        }

        List<FloatingItem> previous = new ArrayList<>(floatingItems);
        floatingItems.clear();
        lastSelectedSlot = client.player.getInventory().selectedSlot;

        List<InventoryItem> inventoryItems = collectFilteredInventory(client);
        int total = inventoryItems.size();
        LuciiLegacy legacy = ClientLuciiState.legacy();
        int charges = ClientLuciiState.ardynWarpCharges();
        for (int i = 0; i < total; i++) {
            InventoryItem inventoryItem = inventoryItems.get(i);
            int index = i + 1;
            float angle = getRingAngleDegrees(legacy, charges, index, total);
            FloatingItem existing = findByKey(previous, inventoryItem.key);
            FloatingItem floatingItem;
            if (existing == null) {
                floatingItem = new FloatingItem(inventoryItem.key, inventoryItem.slot, inventoryItem.stack, angle, index);
                floatingItem.innerTarget = shouldUseArdynInnerRing(legacy, charges, index, total);
                floatingItem.innerProgress = floatingItem.innerTarget ? 1.0F : 0.0F;
            } else {
                floatingItem = existing.update(inventoryItem.stack, angle, index);
            }
            floatingItems.add(floatingItem);
        }

        if (floatingItems.isEmpty()) {
            targetedItem = null;
            lastTargetIndex = -1;
        }
    }

    private static FloatingItem findByKey(List<FloatingItem> items, String key) {
        for (FloatingItem item : items) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private static List<InventoryItem> collectFilteredInventory(MinecraftClient client) {
        List<InventoryItem> items = new ArrayList<>();
        int selectedSlot = client.player.getInventory().selectedSlot;

        for (int slot = 0; slot < client.player.getInventory().main.size(); slot++) {
            ItemStack stack = client.player.getInventory().main.get(slot);
            if (slot != selectedSlot && !stack.isEmpty() && currentFilter.matches(stack)) {
                items.add(new InventoryItem("main:" + slot, slot, stack.copyWithCount(stack.getCount())));
            }
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        if (!offhandStack.isEmpty() && currentFilter.matches(offhandStack)) {
            items.add(new InventoryItem("offhand", -1, offhandStack.copyWithCount(offhandStack.getCount())));
        }

        items.sort(Comparator.comparingInt(item -> item.slot));
        return items;
    }

    private static Vec3d getItemPosition(
            FloatingItem item,
            Vec3d playerPos,
            float time,
            float tickDelta,
            LuciiLegacy legacy,
            int charges,
            int total,
            float guardProgress
    ) {
        float effectiveGuardProgress = legacy == LuciiLegacy.NOCTIS
                ? MathHelper.clamp(guardProgress, 0.0F, 1.0F)
                : 0.0F;
        float direction = item.innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        float animationAngle = item.closing
                ? item.closeAngle
                : item.angle + time * direction;
        double angle = Math.toRadians(animationAngle);
        double normalRingRadius = MathHelper.lerp(
                item.innerProgress,
                (float) RADIUS,
                (float) INNER_RADIUS
        );
        double ringRadius = MathHelper.lerp(
                effectiveGuardProgress,
                (float) normalRingRadius,
                (float) INNER_RADIUS
        );
        double radiusProgress = item.radiusProgress(tickDelta);
        double radius = ringRadius * radiusProgress;
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;

        float positionTime = item.closing ? item.closeTime : time;
        double normalY = ORBIT_Y_OFFSET
                + Math.sin(positionTime * 0.05F + item.index)
                * BOB_HEIGHT
                * radiusProgress;
        int layer = guardLayerForIndex(item.index);
        double guardY = guardLayerYOffset(layer)
                + Math.sin(positionTime * 0.045F + item.index * 0.8F)
                * GUARD_BOB_HEIGHT
                * radiusProgress;
        double y = MathHelper.lerp(
                effectiveGuardProgress,
                (float) normalY,
                (float) guardY
        );
        return playerPos.add(x, y, z);
    }

    private static Vec3d getRemoteItemPosition(
            RemoteFloatingItem item,
            int index,
            Vec3d playerPos,
            float time,
            float tickDelta,
            LuciiLegacy legacy,
            int charges,
            int total,
            float guardProgress
    ) {
        float effectiveGuardProgress = legacy == LuciiLegacy.NOCTIS
                ? MathHelper.clamp(guardProgress, 0.0F, 1.0F)
                : 0.0F;
        float direction = item.innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        float normalBaseAngle = getRingAngleDegrees(legacy, charges, index, total);
        float guardBaseAngle = getGuardRingAngleDegrees(index, total);
        float baseAngle = lerpAngleDegrees(
                normalBaseAngle,
                guardBaseAngle,
                effectiveGuardProgress
        );
        float animationAngle = item.closeTicks > 0
                ? item.closeAngle
                : baseAngle + time * direction;
        item.lastRenderedAngle = animationAngle;
        item.lastRenderedTime = time;

        double angle = Math.toRadians(animationAngle);
        double normalRingRadius = MathHelper.lerp(
                item.innerProgress,
                (float) RADIUS,
                (float) INNER_RADIUS
        );
        double ringRadius = MathHelper.lerp(
                effectiveGuardProgress,
                (float) normalRingRadius,
                (float) INNER_RADIUS
        );
        double radiusProgress = item.radiusProgress(tickDelta);
        double radius = ringRadius * radiusProgress;
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;

        float positionTime = item.closeTicks > 0 ? item.closeTime : time;
        double normalY = ORBIT_Y_OFFSET
                + Math.sin(positionTime * 0.05F + index)
                * BOB_HEIGHT
                * radiusProgress;
        int layer = guardLayerForIndex(index);
        double guardY = guardLayerYOffset(layer)
                + Math.sin(positionTime * 0.045F + index * 0.8F)
                * GUARD_BOB_HEIGHT
                * radiusProgress;
        double y = MathHelper.lerp(
                effectiveGuardProgress,
                (float) normalY,
                (float) guardY
        );
        return playerPos.add(x, y, z);
    }

    private static float getItemAngle(FloatingItem item, float time) {
        float direction = item.innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        return item.angle + time * direction;
    }

    private static FloatingItem findTargetedItem(MinecraftClient client, float time) {
        if (floatingItems.isEmpty() || currentPlayerPos == null) {
            return null;
        }

        Vec3d lookDir = client.player.getRotationVec(1.0F);
        Vec3d eyePos = currentPlayerPos.add(0.0D, client.player.getEyeHeight(client.player.getPose()), 0.0D);
        double lookYaw = Math.toDegrees(Math.atan2(lookDir.x, lookDir.z));
        double lookPitch = Math.toDegrees(Math.atan2(lookDir.y, Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z)));
        double halfSector = (360.0D / floatingItems.size()) * 0.6D;

        FloatingItem bestItem = null;
        double bestScore = -999.0D;
        UUID ownerUuid = client.player.getUuid();
        int total = floatingItems.size();
        for (FloatingItem item : floatingItems) {
            int layer = guardLayerForIndex(item.index);
            float itemTime = time
                    + currentGuardLayerOffset(ownerUuid, layer)
                    * localGuardFormationProgress;
            Vec3d itemPos = getItemPosition(
                    item,
                    currentPlayerPos,
                    itemTime,
                    1.0F,
                    ClientLuciiState.legacy(),
                    ClientLuciiState.ardynWarpCharges(),
                    total,
                    localGuardFormationProgress
            );
            double dx = itemPos.x - currentPlayerPos.x;
            double dz = itemPos.z - currentPlayerPos.z;
            double itemYaw = Math.toDegrees(Math.atan2(dx, dz));
            double yawDiff = Math.abs(MathHelper.wrapDegrees((float) (lookYaw - itemYaw)));

            double dy = itemPos.y - eyePos.y;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            double itemPitch = Math.toDegrees(Math.atan2(dy, horizontalDistance));
            double pitchDiff = Math.abs(lookPitch - itemPitch);

            if (yawDiff < halfSector && pitchDiff < 45.0D) {
                double score = -yawDiff - pitchDiff * 0.5D;
                if (score > bestScore) {
                    bestScore = score;
                    bestItem = item;
                }
            }
        }

        return bestItem;
    }

    private static void equipTargetedItem(MinecraftClient client) {
        if (targetedItem == null || client.player == null || client.getNetworkHandler() == null) {
            sendActionbar(client, "Royal Arms: no targeted item");
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(targetedItem.slot);
        ClientPlayNetworking.send(LegacyOfTheLucii.ROYAL_ARMS_EQUIP_PACKET, buf);
        sendActionbar(client, "Royal Arms equipped: " + targetedItem.stack.getName().getString());
    }

    private static void sendRoyalArmsToggle(boolean active) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(active);
        ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_TOGGLE_PACKET, buf);
    }

    private static int toggleLockDuration(boolean activating) {
        return activating ? APPEAR_TICKS : DISAPPEAR_TICKS;
    }

    private static void sendRoyalArmsFilter(RoyalArmsInventoryFilter filter) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(filter.ordinal());
        ClientPlayNetworking.send(LuciiNetwork.ROYAL_ARMS_FILTER_PACKET, buf);
    }

    private static void sendActionbar(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static void tickClosingAura() {
        if (floatingItems.isEmpty()) {
            return;
        }

        boolean hasClosing = false;
        for (FloatingItem item : floatingItems) {
            if (item.closing) {
                hasClosing = true;
                item.closeTicks++;
            }
        }

        if (hasClosing) {
            floatingItems.removeIf(FloatingItem::finishedClosing);
            if (floatingItems.isEmpty()) {
                targetedItem = null;
                lastTargetIndex = -1;
                lastPlayerPos = null;
                currentPlayerPos = null;
            }
        }
    }

    private static boolean hasClosingLocalItems() {
        for (FloatingItem item : floatingItems) {
            if (item.closing) {
                return true;
            }
        }
        return false;
    }

    private static void updateLocalPlayerPosition(MinecraftClient client) {
        Vec3d playerPos = client.player.getPos();
        if (currentPlayerPos == null) {
            lastPlayerPos = playerPos;
            currentPlayerPos = playerPos;
            return;
        }

        lastPlayerPos = currentPlayerPos;
        currentPlayerPos = playerPos;
    }

    private static void tickRemoteVisuals() {
        if (remoteVisuals.isEmpty()) {
            return;
        }

        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, RemoteRoyalArmsVisual> entry : remoteVisuals.entrySet()) {
            RemoteRoyalArmsVisual visual = entry.getValue();
            for (RemoteFloatingItem item : visual.items) {
                if (visual.closing) {
                    item.closeTicks++;
                } else {
                    item.spawnTicks = Math.min(APPEAR_TICKS, item.spawnTicks + 1);
                }
            }

            if (visual.closing && visual.items.stream().allMatch(RemoteFloatingItem::finishedClosing)) {
                finished.add(entry.getKey());
            }
        }

        for (UUID uuid : finished) {
            remoteVisuals.remove(uuid);
            clearGuardEffects(uuid);
        }
    }

    private static void startClosingAura() {
        if (floatingItems.isEmpty()) {
            clearAura();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        UUID ownerUuid = client.player == null ? null : client.player.getUuid();
        for (FloatingItem item : floatingItems) {
            item.closing = true;
            item.closeTicks = 0;
            int layer = guardLayerForIndex(item.index);
            float layerOffset = ownerUuid == null
                    ? 0.0F
                    : currentGuardLayerOffset(ownerUuid, layer)
                    * localGuardFormationProgress;
            item.closeTime = orbitTime + layerOffset;
            item.closeAngle = getItemAngle(item, item.closeTime);
            item.closeLookAngle = item.closeAngle;
            item.closeSpinAngle = (itemSpinAngle + item.spinPhase) % 360.0F;
            item.highlightScale = 1.0F;
        }
        targetedItem = null;
        lastTargetIndex = -1;
    }

    private static void clearAura() {
        floatingItems.clear();
        targetedItem = null;
        lastTargetIndex = -1;
        lastPlayerPos = null;
        currentPlayerPos = null;
        lastSelectedSlot = -1;
        previousOrbitTime = 0.0F;
        orbitTime = 0.0F;
        previousItemSpinAngle = 0.0F;
        itemSpinAngle = 0.0F;
        initialAuraAppearance = false;
        wasSneaking = false;
        orbitPausedByDoubleSneak = false;
        sneakDoubleTapTicks = 0;
    }

    public static void beginGuardBlock(
            UUID ownerUuid,
            Vec3d interceptPos,
            Vec3d incomingVelocity,
            int travelTicks,
            int layer
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || client.world == null
                || layer < 0
                || layer >= GUARD_LAYER_COUNT) {
            return;
        }

        int durationTicks = MathHelper.clamp(
                travelTicks,
                1,
                GUARD_MAX_APPROACH_TICKS
        );
        UUID selfUuid = client.player.getUuid();
        if (ownerUuid.equals(selfUuid)) {
            if (floatingItems.isEmpty()) {
                return;
            }

            Vec3d playerPos = currentPlayerPos == null
                    ? client.player.getPos()
                    : currentPlayerPos;
            float targetAngle = guardAngleDegrees(interceptPos, playerPos);
            List<Float> itemAngles = new ArrayList<>();
            int total = floatingItems.size();
            float layerOffset = currentGuardLayerOffset(ownerUuid, layer)
                    * localGuardFormationProgress;
            for (FloatingItem item : floatingItems) {
                if (item.closing || guardLayerForIndex(item.index) != layer) {
                    continue;
                }

                Vec3d itemPos = getItemPosition(
                        item,
                        playerPos,
                        orbitTime + layerOffset,
                        1.0F,
                        ClientLuciiState.legacy(),
                        ClientLuciiState.ardynWarpCharges(),
                        total,
                        localGuardFormationProgress
                );
                itemAngles.add(guardAngleDegrees(itemPos, playerPos));
            }

            scheduleGuardOrbitBoost(
                    ownerUuid,
                    layer,
                    itemAngles,
                    targetAngle,
                    currentOrbitSpeed(client),
                    durationTicks
            );
            return;
        }

        AbstractClientPlayerEntity owner = findPlayer(client, ownerUuid);
        RemoteRoyalArmsVisual visual = remoteVisuals.get(ownerUuid);
        if (owner == null || visual == null || visual.items.isEmpty()) {
            return;
        }

        Vec3d playerPos = owner.getPos();
        float normalSpeed = remoteOrbitSpeed(owner);
        float baseTime = client.world.getTime() * normalSpeed
                + remoteExplosionOrbitOffsets.getOrDefault(ownerUuid, 0.0F);
        float guardProgress = remoteGuardFormationProgress.getOrDefault(
                ownerUuid,
                0.0F
        );
        float layerOffset = currentGuardLayerOffset(ownerUuid, layer)
                * guardProgress;
        float targetAngle = guardAngleDegrees(interceptPos, playerPos);
        List<Float> itemAngles = new ArrayList<>();
        int total = visual.items.size();
        for (int i = 0; i < total; i++) {
            int index = i + 1;
            if (guardLayerForIndex(index) != layer) {
                continue;
            }

            Vec3d itemPos = getRemoteItemPosition(
                    visual.items.get(i),
                    index,
                    playerPos,
                    baseTime + layerOffset,
                    1.0F,
                    visual.legacy,
                    visual.ardynWarpCharges,
                    total,
                    guardProgress
            );
            itemAngles.add(guardAngleDegrees(itemPos, playerPos));
        }

        scheduleGuardOrbitBoost(
                ownerUuid,
                layer,
                itemAngles,
                targetAngle,
                normalSpeed,
                durationTicks
        );
    }

    private static void scheduleGuardOrbitBoost(
            UUID ownerUuid,
            int layer,
            List<Float> itemAngles,
            float targetAngle,
            float normalSpeed,
            int durationTicks
    ) {
        OwnerLayerKey key = new OwnerLayerKey(ownerUuid, layer);
        if (itemAngles.isEmpty()) {
            guardLayerBoosts.remove(key);
            return;
        }

        float normalTravel = Math.max(0.0F, normalSpeed) * durationTicks;
        float selectedForwardTravel = Float.MAX_VALUE;

        for (float itemAngle : itemAngles) {
            float forwardTravel = guardForwardDegrees(itemAngle, targetAngle);

            // Select the first weapon in the forward-moving chain that reaches the impact
            // angle no earlier than the projectile. A weapon that would already have passed
            // the angle is skipped in favor of the next one; the orbit never reverses.
            while (forwardTravel + 0.001F < normalTravel) {
                forwardTravel += 360.0F;
            }
            selectedForwardTravel = Math.min(selectedForwardTravel, forwardTravel);
        }

        if (selectedForwardTravel == Float.MAX_VALUE) {
            guardLayerBoosts.remove(key);
            return;
        }

        float extraTravel = Math.max(0.0F, selectedForwardTravel - normalTravel);
        if (extraTravel <= 0.001F) {
            guardLayerBoosts.remove(key);
            return;
        }

        guardLayerBoosts.put(
                key,
                new GuardOrbitBoost(extraTravel, durationTicks)
        );
    }

    public static void beginExplosionGuard(
            UUID ownerUuid,
            int itemCount,
            float protection
    ) {
        int protectedItems = MathHelper.clamp(itemCount, 1, 7);
        float clampedProtection = MathHelper.clamp(protection, 0.0F, 0.70F);
        clearGuardLayerBoosts(ownerUuid);
        explosionGuardOrbits.put(
                ownerUuid,
                new ExplosionGuardOrbit(protectedItems, clampedProtection)
        );
    }

    public static void updateGuardState(UUID ownerUuid, boolean active) {
        if (!active) {
            clearGuardCombatEffects(ownerUuid);
        }
    }

    private static void tickGuardFormationProgress(MinecraftClient client) {
        previousLocalGuardFormationProgress = localGuardFormationProgress;
        boolean localTarget = client.player != null
                && ClientLuciiState.legacy() == LuciiLegacy.NOCTIS
                && ClientLuciiState.royalArmsActive()
                && RoyalArmsGuardClient.isActive(client.player.getUuid());
        localGuardFormationProgress = approachGuardProgress(
                localGuardFormationProgress,
                localTarget ? 1.0F : 0.0F
        );

        previousRemoteGuardFormationProgress.clear();
        previousRemoteGuardFormationProgress.putAll(remoteGuardFormationProgress);
        for (Map.Entry<UUID, RemoteRoyalArmsVisual> entry : remoteVisuals.entrySet()) {
            UUID ownerUuid = entry.getKey();
            RemoteRoyalArmsVisual visual = entry.getValue();
            boolean target = visual.legacy == LuciiLegacy.NOCTIS
                    && !visual.closing
                    && RoyalArmsGuardClient.isActive(ownerUuid);
            float current = remoteGuardFormationProgress.getOrDefault(
                    ownerUuid,
                    0.0F
            );
            remoteGuardFormationProgress.put(
                    ownerUuid,
                    approachGuardProgress(current, target ? 1.0F : 0.0F)
            );
        }
    }

    private static float approachGuardProgress(float current, float target) {
        float next = MathHelper.lerp(GUARD_FORMATION_LERP, current, target);
        if (Math.abs(next - target) < 0.002F) {
            return target;
        }
        return next;
    }

    private static void advanceGuardLayerBoosts() {
        previousGuardLayerOffsets.clear();
        for (Map.Entry<UUID, float[]> entry : guardLayerOffsets.entrySet()) {
            previousGuardLayerOffsets.put(
                    entry.getKey(),
                    entry.getValue().clone()
            );
        }

        List<OwnerLayerKey> finished = new ArrayList<>();
        for (Map.Entry<OwnerLayerKey, GuardOrbitBoost> entry
                : new ArrayList<>(guardLayerBoosts.entrySet())) {
            OwnerLayerKey key = entry.getKey();
            GuardOrbitBoost boost = entry.getValue();
            float[] offsets = guardLayerOffsets.computeIfAbsent(
                    key.ownerUuid,
                    ignored -> new float[GUARD_LAYER_COUNT]
            );
            offsets[key.layer] += boost.advance();
            if (boost.finished()) {
                finished.add(key);
            }
        }

        for (OwnerLayerKey key : finished) {
            guardLayerBoosts.remove(key);
        }
    }

    private static float maxGuardLayerMotion(UUID ownerUuid) {
        float[] current = guardLayerOffsets.get(ownerUuid);
        if (current == null) {
            return 0.0F;
        }

        float[] previous = previousGuardLayerOffsets.get(ownerUuid);
        float maximum = 0.0F;
        for (int layer = 0; layer < GUARD_LAYER_COUNT; layer++) {
            float oldValue = previous == null ? current[layer] : previous[layer];
            maximum = Math.max(maximum, Math.abs(current[layer] - oldValue));
        }
        return maximum;
    }

    private static float advanceLocalExplosionGuardOrbit(
            UUID ownerUuid,
            float normalSpeed
    ) {
        ExplosionGuardOrbit state = explosionGuardOrbits.get(ownerUuid);
        if (state == null) {
            return normalSpeed;
        }

        float delta = state.advance(normalSpeed);
        if (state.finished()) {
            explosionGuardOrbits.remove(ownerUuid);
        }
        return delta;
    }

    private static void tickRemoteExplosionEffects(UUID selfUuid) {
        previousRemoteExplosionOrbitOffsets.clear();
        previousRemoteExplosionOrbitOffsets.putAll(remoteExplosionOrbitOffsets);

        MinecraftClient client = MinecraftClient.getInstance();
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, ExplosionGuardOrbit> entry
                : new ArrayList<>(explosionGuardOrbits.entrySet())) {
            UUID ownerUuid = entry.getKey();
            if (ownerUuid.equals(selfUuid)) {
                continue;
            }

            AbstractClientPlayerEntity owner = findPlayer(client, ownerUuid);
            if (owner == null) {
                finished.add(ownerUuid);
                continue;
            }

            float normalSpeed = remoteOrbitSpeed(owner);
            float desiredDelta = entry.getValue().advance(normalSpeed);
            remoteExplosionOrbitOffsets.merge(
                    ownerUuid,
                    desiredDelta - normalSpeed,
                    Float::sum
            );
            if (entry.getValue().finished()) {
                finished.add(ownerUuid);
            }
        }

        for (UUID ownerUuid : finished) {
            explosionGuardOrbits.remove(ownerUuid);
        }
    }

    private static float interpolatedRemoteExplosionOrbitOffset(
            UUID ownerUuid,
            float tickDelta
    ) {
        float current = remoteExplosionOrbitOffsets.getOrDefault(
                ownerUuid,
                0.0F
        );
        float previous = previousRemoteExplosionOrbitOffsets.getOrDefault(
                ownerUuid,
                current
        );
        return MathHelper.lerp(tickDelta, previous, current);
    }

    private static float currentGuardLayerOffset(UUID ownerUuid, int layer) {
        float[] offsets = guardLayerOffsets.get(ownerUuid);
        return offsets == null ? 0.0F : offsets[layer];
    }

    private static float interpolatedGuardLayerOffset(
            UUID ownerUuid,
            int layer,
            float tickDelta
    ) {
        float[] current = guardLayerOffsets.get(ownerUuid);
        if (current == null) {
            return 0.0F;
        }

        float[] previous = previousGuardLayerOffsets.get(ownerUuid);
        float oldValue = previous == null ? current[layer] : previous[layer];
        return MathHelper.lerp(tickDelta, oldValue, current[layer]);
    }

    private static float interpolatedRemoteGuardFormationProgress(
            UUID ownerUuid,
            float tickDelta
    ) {
        float current = remoteGuardFormationProgress.getOrDefault(
                ownerUuid,
                0.0F
        );
        float previous = previousRemoteGuardFormationProgress.getOrDefault(
                ownerUuid,
                0.0F
        );
        return MathHelper.lerp(tickDelta, previous, current);
    }

    private static float guardAngleDegrees(Vec3d position, Vec3d playerPos) {
        return (float) Math.toDegrees(Math.atan2(
                position.x - playerPos.x,
                position.z - playerPos.z
        ));
    }

    private static float guardForwardDegrees(float from, float to) {
        float delta = (to - from) % 360.0F;
        return delta < 0.0F ? delta + 360.0F : delta;
    }

    private static int guardLayerForIndex(int index) {
        return switch (Math.floorMod(index - 1, GUARD_LAYER_COUNT)) {
            case 0 -> GUARD_LAYER_MIDDLE;
            case 1 -> GUARD_LAYER_UPPER;
            default -> GUARD_LAYER_LOWER;
        };
    }

    private static int guardLayerItemCount(int total, int layer) {
        int count = 0;
        for (int index = 1; index <= total; index++) {
            if (guardLayerForIndex(index) == layer) {
                count++;
            }
        }
        return count;
    }

    private static int guardLayerRank(int index) {
        return Math.floorDiv(index - 1, GUARD_LAYER_COUNT);
    }

    private static float guardLayerPhase(int layer) {
        return switch (layer) {
            case GUARD_LAYER_UPPER -> 0.0F;
            case GUARD_LAYER_MIDDLE -> 48.0F;
            default -> 96.0F;
        };
    }

    private static float getGuardRingAngleDegrees(int index, int total) {
        int layer = guardLayerForIndex(index);
        int layerCount = Math.max(1, guardLayerItemCount(total, layer));
        int rank = guardLayerRank(index);
        return guardLayerPhase(layer) + rank * 360.0F / layerCount;
    }

    private static double guardLayerYOffset(int layer) {
        return switch (layer) {
            case GUARD_LAYER_UPPER -> GUARD_UPPER_Y_OFFSET;
            case GUARD_LAYER_LOWER -> GUARD_LOWER_Y_OFFSET;
            default -> GUARD_MIDDLE_Y_OFFSET;
        };
    }

    private static float lerpAngleDegrees(
            float from,
            float to,
            float progress
    ) {
        return from + MathHelper.wrapDegrees(to - from)
                * MathHelper.clamp(progress, 0.0F, 1.0F);
    }

    private static void clearGuardLayerBoosts(UUID ownerUuid) {
        guardLayerBoosts.keySet().removeIf(
                key -> key.ownerUuid.equals(ownerUuid)
        );
    }

    private static void clearGuardCombatEffects(UUID ownerUuid) {
        clearGuardLayerBoosts(ownerUuid);
        explosionGuardOrbits.remove(ownerUuid);
    }

    private static void clearGuardEffects(UUID ownerUuid) {
        clearGuardCombatEffects(ownerUuid);
        guardLayerOffsets.remove(ownerUuid);
        previousGuardLayerOffsets.remove(ownerUuid);
        remoteExplosionOrbitOffsets.remove(ownerUuid);
        previousRemoteExplosionOrbitOffsets.remove(ownerUuid);
        remoteGuardFormationProgress.remove(ownerUuid);
        previousRemoteGuardFormationProgress.remove(ownerUuid);
    }

    public static void clearLocalGuardEffects() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            clearGuardCombatEffects(client.player.getUuid());
        }
    }

    public static void clearGuardBlocks() {
        guardLayerBoosts.clear();
        guardLayerOffsets.clear();
        previousGuardLayerOffsets.clear();
        explosionGuardOrbits.clear();
        remoteExplosionOrbitOffsets.clear();
        previousRemoteExplosionOrbitOffsets.clear();
        remoteGuardFormationProgress.clear();
        previousRemoteGuardFormationProgress.clear();
        localGuardFormationProgress = 0.0F;
        previousLocalGuardFormationProgress = 0.0F;
    }

    private record OwnerLayerKey(UUID ownerUuid, int layer) {
    }
    // END PHANTOM_GUARD_THREE_LAYER_V7 METHODS

    public static void updateRemoteVisual(UUID ownerUuid, boolean active, LuciiLegacy legacy, List<ItemStack> stacks, int ardynWarpCharges) {
        if (!active || legacy == LuciiLegacy.NONE) {
            clearGuardCombatEffects(ownerUuid);
            RemoteRoyalArmsVisual existing = remoteVisuals.get(ownerUuid);
            if (existing == null) {
                return;
            }

            existing.closing = true;
            for (RemoteFloatingItem item : existing.items) {
                item.closeTicks = 0;
                item.closeTime = item.lastRenderedTime;
                item.closeAngle = item.lastRenderedAngle;
                item.closeLookAngle = item.lastRenderedLookAngle;
                item.closeSpinAngle = item.lastRenderedSpinAngle;
            }
            return;
        }

        RemoteRoyalArmsVisual previous = remoteVisuals.get(ownerUuid);
        List<RemoteFloatingItem> copiedStacks = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                RemoteFloatingItem previousItem = previous == null ? null : previous.find(stack);
                copiedStacks.add(previousItem == null
                        ? new RemoteFloatingItem(stack.copyWithCount(stack.getCount()))
                        : previousItem.update(stack.copyWithCount(stack.getCount())));
            }
        }
        remoteVisuals.put(ownerUuid, new RemoteRoyalArmsVisual(legacy, copiedStacks, ardynWarpCharges));
    }

    public static void clearRemoteVisuals() {
        remoteVisuals.clear();
        clearGuardBlocks();
        ardynBarrageOwners.clear();
        clearAura();
        lastActive = false;
        toggleLockTicks = 0;
    }

    public static void updateArdynBarrage(UUID ownerUuid, boolean active) {
        if (active) {
            if (!ardynBarrageOwners.contains(ownerUuid)) {
                ardynBarrageOwners.add(ownerUuid);
            }
        } else {
            ardynBarrageOwners.remove(ownerUuid);
        }
    }

    public static void restartAuraAppearance(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID selfUuid = client.player == null ? null : client.player.getUuid();
        if (ownerUuid.equals(selfUuid)) {
            initialAuraAppearance = true;
            for (FloatingItem item : floatingItems) {
                item.spawnTicks = 0;
                item.closing = false;
                item.closeTicks = 0;
                item.highlightScale = 1.0F;
            }
            return;
        }

        RemoteRoyalArmsVisual visual = remoteVisuals.get(ownerUuid);
        if (visual == null) {
            return;
        }

        visual.closing = false;
        for (RemoteFloatingItem item : visual.items) {
            item.spawnTicks = 0;
            item.closeTicks = 0;
        }
    }

    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
        if (client.world == null) {
            return null;
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private record InventoryItem(String key, int slot, ItemStack stack) {
    }

    private static boolean shouldUseArdynInnerRing(LuciiLegacy legacy, int charges, int index, int total) {
        return index <= getArdynInnerRingCount(legacy, charges, total);
    }

    private static int getArdynInnerRingCount(LuciiLegacy legacy, int charges, int total) {
        if (legacy != LuciiLegacy.ARDYN || charges < 3 || total < 2) {
            return 0;
        }

        int stage = MathHelper.clamp(charges / 3, 1, 4);
        int maxInnerCount = Math.max(1, total / 2);
        return Math.max(1, MathHelper.ceil(maxInnerCount * (stage / 4.0F)));
    }

    private static float getRingAngleDegrees(LuciiLegacy legacy, int charges, int index, int total) {
        int innerCount = getArdynInnerRingCount(legacy, charges, total);
        if (innerCount <= 0) {
            return index * 360.0F / total;
        }

        if (index <= innerCount) {
            return index * 360.0F / innerCount;
        }

        int outerCount = total - innerCount;
        if (outerCount <= 0) {
            return index * 360.0F / total;
        }

        int outerIndex = index - innerCount;
        return outerIndex * 360.0F / outerCount;
    }

    private static float easeOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        if (clamped < 0.5F) {
            return 4.0F * clamped * clamped * clamped;
        }

        float shifted = -2.0F * clamped + 2.0F;
        return 1.0F - shifted * shifted * shifted / 2.0F;
    }

    // BEGIN PHANTOM_GUARD_THREE_LAYER_V7 CLASSES
    private static final class GuardOrbitBoost {
        private final float totalExtraAngle;
        private final int durationTicks;
        private int age;
        private float appliedAngle;

        private GuardOrbitBoost(float totalExtraAngle, int durationTicks) {
            this.totalExtraAngle = totalExtraAngle;
            this.durationTicks = Math.max(1, durationTicks);
        }

        private float advance() {
            int nextAge = Math.min(durationTicks, age + 1);
            float progress = easeOutCubic(nextAge / (float) durationTicks);
            float targetAppliedAngle = totalExtraAngle * progress;
            float delta = targetAppliedAngle - appliedAngle;
            appliedAngle = targetAppliedAngle;
            age = nextAge;
            return delta;
        }

        private boolean finished() {
            return age >= durationTicks;
        }
    }

    private static final class ExplosionGuardOrbit {
        private final float startingExtraSpeed;
        private final float protection;
        private int age;

        private ExplosionGuardOrbit(int itemCount, float protection) {
            this.startingExtraSpeed = Math.min(
                    EXPLOSION_GUARD_MAX_EXTRA_SPEED,
                    EXPLOSION_GUARD_BASE_EXTRA_SPEED
                            + itemCount * EXPLOSION_GUARD_EXTRA_SPEED_PER_ITEM
            );
            this.protection = protection;
        }

        private float advance(float normalSpeed) {
            age++;
            if (age <= EXPLOSION_GUARD_HOLD_TICKS) {
                // All weapons remain fixed for a short impact pose, as in the reference.
                return 0.0F;
            }

            int spinAge = age - EXPLOSION_GUARD_HOLD_TICKS;
            float progress = MathHelper.clamp(
                    spinAge / (float) EXPLOSION_GUARD_SPIN_TICKS,
                    0.0F,
                    1.0F
            );
            float decay = 1.0F - easeOutCubic(progress);
            float protectionScale = MathHelper.lerp(protection / 0.70F, 0.82F, 1.0F);
            return normalSpeed + startingExtraSpeed * decay * protectionScale;
        }

        private boolean finished() {
            return age >= EXPLOSION_GUARD_HOLD_TICKS + EXPLOSION_GUARD_SPIN_TICKS;
        }
    }
    // END PHANTOM_GUARD_THREE_LAYER_V7 CLASSES

    private static final class RemoteRoyalArmsVisual {
        private final LuciiLegacy legacy;
        private final List<RemoteFloatingItem> items;
        private int ardynWarpCharges;
        private boolean closing;

        private RemoteRoyalArmsVisual(LuciiLegacy legacy, List<RemoteFloatingItem> items, int ardynWarpCharges) {
            this.legacy = legacy;
            this.items = items;
            this.ardynWarpCharges = ardynWarpCharges;
        }

        private RemoteFloatingItem find(ItemStack stack) {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            for (RemoteFloatingItem item : items) {
                if (Registries.ITEM.getId(item.stack.getItem()).equals(id)) {
                    return item;
                }
            }
            return null;
        }
    }

    private static final class FloatingItem {
        private final String key;
        private final int slot;
        private ItemStack stack;
        private float angle;
        private float targetAngle;
        private int index;
        private final int spinPhase;
        private final int seed;
        private int spawnTicks;
        private boolean closing;
        private int closeTicks;
        private float closeTime;
        private float closeAngle;
        private float closeLookAngle;
        private float closeSpinAngle;
        private float highlightScale = 1.0F;
        private float innerProgress;
        private boolean innerTarget;

        private FloatingItem(String key, int slot, ItemStack stack, float angle, int index) {
            this.key = key;
            this.slot = slot;
            this.stack = stack;
            this.angle = angle;
            this.targetAngle = angle;
            this.index = index;
            this.spinPhase = spinPhaseFor(stack);
            this.seed = slot * 31 + Registries.ITEM.getId(stack.getItem()).hashCode();
        }

        private FloatingItem update(ItemStack newStack, float newTargetAngle, int newIndex) {
            this.stack = newStack;
            this.targetAngle = newTargetAngle;
            this.index = newIndex;
            this.closing = false;
            this.closeTicks = 0;
            return this;
        }

        private float visualScale(float tickDelta) {
            if (closing) {
                return Math.max(0.01F, 1.0F - easeInOutCubic(interpolatedCloseTicks(tickDelta) / (float) DISAPPEAR_TICKS));
            }
            return Math.max(0.01F, easeOutCubic(interpolatedSpawnTicks(tickDelta) / (float) APPEAR_TICKS));
        }

        private float radiusProgress(float tickDelta) {
            if (closing) {
                return Math.max(0.0F, 1.0F - easeInOutCubic(interpolatedCloseTicks(tickDelta) / (float) DISAPPEAR_TICKS));
            }
            return easeOutCubic(interpolatedSpawnTicks(tickDelta) / (float) APPEAR_TICKS);
        }

        private boolean isAppearing() {
            return !closing && spawnTicks < APPEAR_TICKS;
        }

        private float interpolatedSpawnTicks(float tickDelta) {
            return MathHelper.clamp(spawnTicks + tickDelta, 0.0F, APPEAR_TICKS);
        }

        private float interpolatedCloseTicks(float tickDelta) {
            return MathHelper.clamp(closeTicks + tickDelta, 0.0F, DISAPPEAR_TICKS);
        }

        private boolean finishedClosing() {
            return closing && closeTicks >= DISAPPEAR_TICKS;
        }
    }

    private static final class RemoteFloatingItem {
        private ItemStack stack;
        private int spawnTicks;
        private int closeTicks;
        private float closeTime;
        private float closeAngle;
        private float closeLookAngle;
        private float closeSpinAngle;
        private float lastRenderedTime;
        private float lastRenderedAngle;
        private float lastRenderedLookAngle;
        private float lastRenderedSpinAngle;
        private float innerProgress;
        private boolean innerTarget;

        private RemoteFloatingItem(ItemStack stack) {
            this.stack = stack;
        }

        private RemoteFloatingItem update(ItemStack newStack) {
            this.stack = newStack;
            this.closeTicks = 0;
            return this;
        }

        private float visualScale(float tickDelta) {
            if (closeTicks > 0) {
                return Math.max(0.01F, 1.0F - easeInOutCubic(interpolatedCloseTicks(tickDelta) / (float) DISAPPEAR_TICKS));
            }
            return Math.max(0.01F, easeOutCubic(interpolatedSpawnTicks(tickDelta) / (float) APPEAR_TICKS));
        }

        private float radiusProgress(float tickDelta) {
            if (closeTicks > 0) {
                return Math.max(0.0F, 1.0F - easeInOutCubic(interpolatedCloseTicks(tickDelta) / (float) DISAPPEAR_TICKS));
            }
            return easeOutCubic(interpolatedSpawnTicks(tickDelta) / (float) APPEAR_TICKS);
        }

        private boolean isTransitioning() {
            return closeTicks > 0 || spawnTicks < APPEAR_TICKS;
        }

        private float interpolatedSpawnTicks(float tickDelta) {
            return MathHelper.clamp(spawnTicks + tickDelta, 0.0F, APPEAR_TICKS);
        }

        private float interpolatedCloseTicks(float tickDelta) {
            return MathHelper.clamp(closeTicks + tickDelta, 0.0F, DISAPPEAR_TICKS);
        }

        private boolean finishedClosing() {
            return closeTicks >= DISAPPEAR_TICKS;
        }
    }

    private static int spinPhaseFor(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        int hash = 0;
        String value = id.toString();
        for (int i = 0; i < value.length(); i++) {
            hash += value.charAt(i);
        }
        return hash % 360;
    }

    public static boolean isActive() {
        return ClientLuciiState.royalArmsActive();
    }

    private enum LegacyPalette {
        NOCTIS(
                new RenderTint(0.58F, 0.78F, 1.0F, 0.42F),
                new RenderTint(0.72F, 0.88F, 1.0F, 0.68F)
        ),
        ARDYN(
                new RenderTint(1.0F, 0.20F, 0.24F, 0.50F),
                new RenderTint(1.0F, 0.38F, 0.42F, 0.76F)
        );

        private final RenderTint baseTint;
        private final RenderTint targetTint;

        LegacyPalette(RenderTint baseTint, RenderTint targetTint) {
            this.baseTint = baseTint;
            this.targetTint = targetTint;
        }

        private static LegacyPalette forLegacy(LuciiLegacy legacy) {
            return legacy == LuciiLegacy.ARDYN ? ARDYN : NOCTIS;
        }
    }

    private record RenderTint(float red, float green, float blue, float alpha) {
    }

    private static final class TintedItemVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider delegate;
        private final RenderTint tint;

        private TintedItemVertexConsumerProvider(VertexConsumerProvider delegate, RenderTint tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new TintedItemVertexConsumer(delegate.getBuffer(layer), tint);
        }
    }

    private static final class TintedItemVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final RenderTint tint;

        private TintedItemVertexConsumer(VertexConsumer delegate, RenderTint tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            int tintedRed = MathHelper.clamp((int) (red * tint.red), 0, 255);
            int tintedGreen = MathHelper.clamp((int) (green * tint.green), 0, 255);
            int tintedBlue = MathHelper.clamp((int) (blue * tint.blue), 0, 255);
            int tintedAlpha = MathHelper.clamp((int) (alpha * tint.alpha), 0, 255);
            delegate.color(tintedRed, tintedGreen, tintedBlue, tintedAlpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            int tintedRed = MathHelper.clamp((int) (red * tint.red), 0, 255);
            int tintedGreen = MathHelper.clamp((int) (green * tint.green), 0, 255);
            int tintedBlue = MathHelper.clamp((int) (blue * tint.blue), 0, 255);
            int tintedAlpha = MathHelper.clamp((int) (alpha * tint.alpha), 0, 255);
            delegate.fixedColor(tintedRed, tintedGreen, tintedBlue, tintedAlpha);
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            delegate.vertex(matrix, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            delegate.normal(matrix, x, y, z);
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.vertex(
                    x,
                    y,
                    z,
                    red * tint.red,
                    green * tint.green,
                    blue * tint.blue,
                    alpha * tint.alpha,
                    u,
                    v,
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ
            );
        }
    }
}
