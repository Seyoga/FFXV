package ru.siyoga.legacyofthelucii.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWallAbility;

import java.util.List;

public final class LuciiRingItem extends Item {
    private final LuciiLegacy unlocksLegacy;

    public LuciiRingItem(LuciiLegacy unlocksLegacy, Settings settings) {
        super(settings);
        this.unlocksLegacy = unlocksLegacy;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            LuciiPlayerState state = LuciiPlayerStates.get(user);
            state.unlockLegacy(unlocksLegacy);
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                RoyalArmsWallAbility.deactivate(serverPlayer, false);
                LuciiNetwork.sendState(serverPlayer);
                LuciiNetwork.broadcastRoyalArmsVisual(serverPlayer);
            }
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            user.sendMessage(Text.translatable("message.legacyofthelucii.legacy_unlocked", unlocksLegacy.id()), true);
        }

        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.legacyofthelucii.unlocks", Text.translatable("legacy.legacyofthelucii." + unlocksLegacy.id())));
    }
}
