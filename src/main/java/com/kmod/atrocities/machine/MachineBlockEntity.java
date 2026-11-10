package com.kmod.atrocities.machine;

import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Base das maquinas biologicas: inventario, reserva de nutrientes alimentada por Biomassa,
 * progresso, sincronizacao com o menu, automacao (funis) e estado aceso.
 * <p>
 * Cada tick: reabastece nutrientes, pergunta a subclasse se ha trabalho ({@link #processDuration}),
 * paga o custo em nutrientes e avanca. Trocar o conteudo dos slots de entrada zera o progresso.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_NUTRIENTS = 2000;
    public static final int NUTRIENTS_PER_BIOMASS = 100;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_NUTRIENTS = 2;
    public static final int DATA_EXTRA = 3;

    private final MachineKind kind;
    protected final ItemStackHandler inventory;
    private final IItemHandler automation;
    private final ContainerData data;

    protected int progress;
    protected int maxProgress;
    protected int nutrients;
    private int inputSignature;
    private boolean signatureKnown;

    protected MachineBlockEntity(BlockEntityType<?> type, MachineKind kind, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = kind;
        this.inventory = new ItemStackHandler(kind.slotCount()) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return kind.accepts(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                onInventoryChanged();
            }
        };
        this.automation = new AutomationHandler();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> progress;
                    case DATA_MAX_PROGRESS -> maxProgress;
                    case DATA_NUTRIENTS -> nutrients;
                    default -> extraData(index - DATA_EXTRA);
                };
            }

            @Override
            public void set(int index, int value) {
                // Somente leitura: o cliente recebe os valores por SimpleContainerData.
            }

            @Override
            public int getCount() {
                return kind.dataCount();
            }
        };
    }

    public MachineKind kind() {
        return kind;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    /** Visao do inventario para funis e tubos: insere nas entradas/combustivel, extrai so das saidas. */
    public IItemHandler automation() {
        return automation;
    }

    public ContainerData data() {
        return data;
    }

    // ------------------------------------------------------------------ ganchos das maquinas

    /** Duracao do processo atual em ticks, ou 0 se nao ha trabalho possivel agora. */
    protected abstract int processDuration(ServerLevel level);

    /** Conclui o processo: consome entradas e gera saidas. */
    protected abstract void finish(ServerLevel level);

    /** Nutrientes gastos neste tick de trabalho. */
    protected int nutrientCost(int currentProgress) {
        return 1;
    }

    /** Antes de avaliar o processo (ex.: o Extrator esvazia o acumulador). */
    protected void beforeProcess(ServerLevel level) {
    }

    /** Cada tick em que o processo avancou (sons e particulas). */
    protected void workingTick(ServerLevel level) {
    }

    /** Cada tick em que havia trabalho mas faltaram nutrientes. */
    protected void starvingTick(ServerLevel level) {
    }

    /** Conteudo dos slots de entrada mudou; o progresso ja foi zerado. */
    protected void onInputChanged() {
    }

    protected int extraData(int index) {
        return 0;
    }

    protected void saveExtra(CompoundTag tag) {
    }

    protected void loadExtra(CompoundTag tag) {
    }

    // ------------------------------------------------------------------ tick

    public void serverTick(ServerLevel level, BlockState state) {
        refuel(level);
        beforeProcess(level);

        int signature = inputSignature();
        if (!signatureKnown) {
            signatureKnown = true;
            inputSignature = signature;
        } else if (signature != inputSignature) {
            inputSignature = signature;
            progress = 0;
            onInputChanged();
            setChanged();
        }

        boolean active = false;
        int duration = processDuration(level);
        if (duration > 0) {
            maxProgress = duration;
            int cost = nutrientCost(progress);
            if (nutrients >= cost) {
                nutrients -= cost;
                progress++;
                active = true;
                workingTick(level);
                if (progress >= maxProgress) {
                    progress = 0;
                    finish(level);
                }
            } else {
                starvingTick(level);
            }
            setChanged();
        } else if (progress != 0 || maxProgress != 0) {
            progress = 0;
            maxProgress = 0;
            setChanged();
        }
        MachineBlock.setLit(level, worldPosition, state, active);
    }

    /** Converte Biomassa do slot de combustivel em nutrientes enquanto houver espaco na reserva. */
    private void refuel(ServerLevel level) {
        for (int slot = 0; slot < kind.slotCount(); slot++) {
            if (kind.role(slot) != MachineKind.Role.FUEL) {
                continue;
            }
            boolean fed = false;
            while (nutrients + NUTRIENTS_PER_BIOMASS <= MAX_NUTRIENTS
                    && inventory.getStackInSlot(slot).is(ModItems.BIOMASS.get())) {
                inventory.extractItem(slot, 1, false);
                nutrients += NUTRIENTS_PER_BIOMASS;
                fed = true;
            }
            if (fed) {
                playSound(level, ModSounds.MACHINE_REFUEL.get(), 0.5f, 0.8f + level.random.nextFloat() * 0.2f);
                setChanged();
            }
        }
    }

    private int inputSignature() {
        int hash = 1;
        for (int slot = 0; slot < kind.slotCount(); slot++) {
            if (kind.role(slot) == MachineKind.Role.INPUT) {
                hash = 31 * hash + ItemStack.hashItemAndComponents(inventory.getStackInSlot(slot));
            }
        }
        return hash;
    }

    // ------------------------------------------------------------------ utilitarios das subclasses

    protected boolean canOutput(int slot, ItemStack result) {
        ItemStack current = inventory.getStackInSlot(slot);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    protected void output(int slot, ItemStack result) {
        ItemStack current = inventory.getStackInSlot(slot);
        if (current.isEmpty()) {
            inventory.setStackInSlot(slot, result.copy());
        } else {
            inventory.setStackInSlot(slot, current.copyWithCount(current.getCount() + result.getCount()));
        }
    }

    protected Vec3 center() {
        return Vec3.atCenterOf(worldPosition);
    }

    protected void playSound(Level level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private void onInventoryChanged() {
        setChanged();
        if (kind.syncsContents() && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void dropContents(Level level) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MachineMenu(kind, containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistencia e sincronizacao

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.putInt("max_progress", maxProgress);
        tag.putInt("nutrients", nutrients);
        saveExtra(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        progress = tag.getInt("progress");
        maxProgress = tag.getInt("max_progress");
        nutrients = Math.min(tag.getInt("nutrients"), MAX_NUTRIENTS);
        loadExtra(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return kind.syncsContents() ? saveWithoutMetadata(registries) : super.getUpdateTag(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return kind.syncsContents() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return kind.role(slot) == MachineKind.Role.OUTPUT ? stack : inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return kind.role(slot) == MachineKind.Role.OUTPUT
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    }
}
