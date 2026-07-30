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
    private static final String CATEGORY = "key.categories.legacyofthelucii";

    private static KeyBinding toggleKey;
    private static KeyBinding filterKey;

    private static final List<FloatingItem> floatingItems = new ArrayList<>();
    private static final Map<UUID, RemoteRoyalArmsVisual> remoteVisuals = new HashMap<>();
    private static final List<UUID> ardynBarrageOwners = new ArrayList<>();
    private static boolean lastActive;
    private static RoyalArmsInventoryFilter currentFilter = RoyalArmsInventoryFilter.ALL;
    private static Vec3d lastPlayerPos;
    private static Vec3d currentPlayerPos;
    private static int refreshTimer;
    private static int lastTargetIndex = -1;
    private static FloatingItem targetedItem;
    private static int attackEquipCooldown;
    private static int toggleLockTicks;
    private static float previousOrbitTime;
    private static float orbitTime;
    private static float previousItemSpinAngle;
    private static float itemSpinAngle;
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
        if (toggleLockTicks > 0) {
            toggleLockTicks--;
        }

        while (toggleKey.wasPressed()) {
            if (!ClientLuciiState.royalArmsActive() && !ClientLuciiState.hasLegacy()) {
                sendActionbar(client, Text.translatable("message.legacyofthelucii.royal_arms.requires_legacy").getString());
                continue;
            }

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
                sendActionbar(client, "Royal Arms: active");
            } else {
                startClosingAura();
                sendActionbar(client, "Royal Arms: inactive");
            }
            toggleLockTicks = Math.max(toggleLockTicks, toggleLockDuration(active));
            lastActive = active;
        }

        tickClosingAura();
        tickRemoteVisuals();

        if (client.player != null && client.world != null && (active || hasClosingLocalItems())) {
            updateLocalPlayerPosition(client);
        }

        if (!active || client.player == null || client.world == null) {
            return;
        }

        updateOrbitSpeedState(client);
        previousOrbitTime = orbitTime;
        previousItemSpinAngle = itemSpinAngle;
        boolean transitioning = hasTransitioningLocalItems();
        if (!transitioning) {
            orbitTime += currentOrbitSpeed(client);
            itemSpinAngle = (itemSpinAngle + ITEM_SPIN_SPEED) % 360.0F;
        }

        if (attackEquipCooldown > 0) {
            attackEquipCooldown--;
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
            item.targetAngle = getRingAngleDegrees(ClientLuciiState.legacy(), charges, item.index, floatingItems.size());
            float diff = MathHelper.wrapDegrees(item.targetAngle - item.angle);
            item.angle += diff * 0.12F;
            item.highlightScale = MathHelper.lerp(0.16F, item.highlightScale, item == targetedItem ? TARGET_ITEM_SCALE : 1.0F);
            item.spawnTicks = Math.min(APPEAR_TICKS, item.spawnTicks + 1);
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
        float tickDelta = context.tickDelta();
        float time = MathHelper.lerp(tickDelta, previousOrbitTime, orbitTime);
        float spinAngle = MathHelper.lerp(tickDelta, previousItemSpinAngle, itemSpinAngle);
        float spinTime = client.world.getTime() + tickDelta;

        if (!floatingItems.isEmpty()
                && lastPlayerPos != null
                && currentPlayerPos != null
                && !RoyalArmsBindClient.isBinding(client.player.getUuid())
                && !ardynBarrageOwners.contains(client.player.getUuid())) {
            Vec3d playerPos = lastPlayerPos.lerp(currentPlayerPos, tickDelta);
            for (FloatingItem item : floatingItems) {
                Vec3d itemPos = getItemPosition(item, playerPos, time, tickDelta, ClientLuciiState.legacy(), ClientLuciiState.ardynWarpCharges(), floatingItems.size());
                renderFloatingItem(context, item, itemPos, cameraPos, playerPos, spinAngle, tickDelta);
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
        MatrixStack matrices = context.matrixStack();
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
            float time = spinTime * remoteOrbitSpeed(owner);
            int total = visual.items.size();
            LegacyPalette palette = LegacyPalette.forLegacy(visual.legacy);
            for (int i = 0; i < total; i++) {
                ItemStack stack = visual.items.get(i).stack;
                RemoteFloatingItem item = visual.items.get(i);
                item.innerTarget = shouldUseArdynInnerRing(visual.legacy, visual.ardynWarpCharges, i + 1, total);
                item.innerProgress = MathHelper.lerp(0.12F, item.innerProgress, item.innerTarget ? 1.0F : 0.0F);
                Vec3d itemPos = getRemoteItemPosition(item, i + 1, playerPos, time, tickDelta, visual.legacy, visual.ardynWarpCharges, total);
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

    private static Vec3d getItemPosition(FloatingItem item, Vec3d playerPos, float time, float tickDelta, LuciiLegacy legacy, int charges, int total) {
        float direction = item.innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        float animationAngle = item.closing
                ? item.closeAngle
                : item.angle + time * direction;
        double angle = Math.toRadians(animationAngle);
        double ringRadius = MathHelper.lerp(item.innerProgress, (float) RADIUS, (float) INNER_RADIUS);
        double radius = ringRadius * item.radiusProgress(tickDelta);
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;
        float positionTime = item.closing ? item.closeTime : time;
        double y = ORBIT_Y_OFFSET + Math.sin(positionTime * 0.05F + item.index) * BOB_HEIGHT * item.radiusProgress(tickDelta);
        return playerPos.add(x, y, z);
    }

    private static Vec3d getRemoteItemPosition(RemoteFloatingItem item, int index, Vec3d playerPos, float time, float tickDelta, LuciiLegacy legacy, int charges, int total) {
        float direction = item.innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        float animationAngle = item.closeTicks > 0
                ? item.closeAngle
                : getRingAngleDegrees(legacy, charges, index, total) + time * direction;
        item.lastRenderedAngle = animationAngle;
        item.lastRenderedTime = time;
        double angle = Math.toRadians(animationAngle);
        double ringRadius = MathHelper.lerp(item.innerProgress, (float) RADIUS, (float) INNER_RADIUS);
        double radius = ringRadius * item.radiusProgress(tickDelta);
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;
        float positionTime = item.closeTicks > 0 ? item.closeTime : time;
        double y = ORBIT_Y_OFFSET + Math.sin(positionTime * 0.05F + index) * BOB_HEIGHT * item.radiusProgress(tickDelta);
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
        for (FloatingItem item : floatingItems) {
            Vec3d itemPos = getItemPosition(item, currentPlayerPos, time, 1.0F, ClientLuciiState.legacy(), ClientLuciiState.ardynWarpCharges(), floatingItems.size());
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

    private static boolean hasTransitioningLocalItems() {
        for (FloatingItem item : floatingItems) {
            if (item.isTransitioning()) {
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
        }
    }

    private static void startClosingAura() {
        if (floatingItems.isEmpty()) {
            clearAura();
            return;
        }

        for (FloatingItem item : floatingItems) {
            item.closing = true;
            item.closeTicks = 0;
            item.closeTime = orbitTime;
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
        previousOrbitTime = 0.0F;
        orbitTime = 0.0F;
        previousItemSpinAngle = 0.0F;
        itemSpinAngle = 0.0F;
        wasSneaking = false;
        orbitPausedByDoubleSneak = false;
        sneakDoubleTapTicks = 0;
    }

    public static void updateRemoteVisual(UUID ownerUuid, boolean active, LuciiLegacy legacy, List<ItemStack> stacks, int ardynWarpCharges) {
        if (!active || legacy == LuciiLegacy.NONE) {
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
            restartAuraAppearance(ownerUuid);
        }
    }

    public static void restartAuraAppearance(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID selfUuid = client.player == null ? null : client.player.getUuid();
        if (ownerUuid.equals(selfUuid)) {
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

        private boolean isTransitioning() {
            return closing || spawnTicks < APPEAR_TICKS;
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
