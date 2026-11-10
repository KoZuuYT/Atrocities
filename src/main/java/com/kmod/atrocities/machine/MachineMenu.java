package com.kmod.atrocities.machine;

import com.kmod.atrocities.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Menu generico das maquinas; o layout vem de {@link MachineKind}. */
public class MachineMenu extends AbstractContainerMenu {

    private static final int PLAYER_SLOTS = 36;

    private final MachineKind kind;
    private final ContainerData data;
    private final BlockPos pos;
    private final @Nullable MachineBlockEntity blockEntity;
    private final int machineSlots;

    /** Lado do cliente: inventario espelho, preenchido pela sincronizacao do menu. */
    public MachineMenu(MachineKind kind, int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(kind, containerId, playerInventory, buf.readBlockPos(), clientHandler(kind),
                new SimpleContainerData(kind.dataCount()), null);
    }

    /** Lado do servidor. */
    public MachineMenu(MachineKind kind, int containerId, Inventory playerInventory, MachineBlockEntity blockEntity) {
        this(kind, containerId, playerInventory, blockEntity.getBlockPos(), blockEntity.inventory(),
                blockEntity.data(), blockEntity);
    }

    private MachineMenu(MachineKind kind, int containerId, Inventory playerInventory, BlockPos pos,
                        IItemHandler handler, ContainerData data, @Nullable MachineBlockEntity blockEntity) {
        super(ModMenus.type(kind), containerId);
        this.kind = kind;
        this.data = data;
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.machineSlots = kind.slotCount();

        for (int i = 0; i < kind.slotCount(); i++) {
            MachineKind.SlotSpec spec = kind.slots().get(i);
            if (spec.role() == MachineKind.Role.OUTPUT) {
                addSlot(new SlotItemHandler(handler, i, spec.x(), spec.y()) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            } else {
                addSlot(new SlotItemHandler(handler, i, spec.x(), spec.y()));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    private static IItemHandler clientHandler(MachineKind kind) {
        return new ItemStackHandler(kind.slotCount()) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return kind.accepts(slot, stack);
            }
        };
    }

    public MachineKind kind() {
        return kind;
    }

    public BlockPos pos() {
        return pos;
    }

    public int progress() {
        return data.get(MachineBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        return data.get(MachineBlockEntity.DATA_MAX_PROGRESS);
    }

    public float progressRatio() {
        int max = maxProgress();
        return max <= 0 ? 0f : Math.min(1f, progress() / (float) max);
    }

    public int nutrients() {
        return data.get(MachineBlockEntity.DATA_NUTRIENTS);
    }

    public int extra(int index) {
        return data.get(MachineBlockEntity.DATA_EXTRA + index);
    }

    /** Item atual num slot da maquina (para as previsoes da tela). */
    public ItemStack machineItem(int slot) {
        return slots.get(slot).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = machineSlots;
        int hotbarStart = machineSlots + 27;
        int end = machineSlots + PLAYER_SLOTS;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, playerStart, end, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            boolean moved = index < hotbarStart
                    ? moveItemStackTo(stack, hotbarStart, end, false)
                    : moveItemStackTo(stack, playerStart, hotbarStart, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || Container.stillValidBlockEntity(blockEntity, player);
    }
}
