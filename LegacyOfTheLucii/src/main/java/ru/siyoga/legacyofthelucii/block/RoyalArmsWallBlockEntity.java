package ru.siyoga.legacyofthelucii.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWallAbility;

import java.util.UUID;

public final class RoyalArmsWallBlockEntity extends BlockEntity {
    private static final String OWNER_KEY = "Owner";
    private static final String SOURCE_STATE_KEY = "SourceState";

    private UUID ownerUuid;
    private BlockState sourceState = Blocks.STONE.getDefaultState();

    public RoyalArmsWallBlockEntity(BlockPos pos, BlockState state) {
        super(LegacyBlocks.ROYAL_ARMS_WALL_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, RoyalArmsWallBlockEntity blockEntity) {
        if (world instanceof ServerWorld serverWorld && !RoyalArmsWallAbility.isManagedSegment(serverWorld, pos, blockEntity.ownerUuid)) {
            serverWorld.removeBlock(pos, false);
        }
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public BlockState sourceState() {
        return sourceState;
    }

    public void configure(UUID ownerUuid, BlockState sourceState) {
        this.ownerUuid = ownerUuid;
        this.sourceState = sourceState;
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.containsUuid(OWNER_KEY)) {
            ownerUuid = nbt.getUuid(OWNER_KEY);
        }
        if (nbt.contains(SOURCE_STATE_KEY, NbtCompound.COMPOUND_TYPE)) {
            sourceState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound(SOURCE_STATE_KEY));
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (ownerUuid != null) {
            nbt.putUuid(OWNER_KEY, ownerUuid);
        }
        nbt.put(SOURCE_STATE_KEY, NbtHelper.fromBlockState(sourceState));
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
}
