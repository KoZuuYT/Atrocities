package com.kmod.atrocities.machine;

import com.kmod.atrocities.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Bloco das maquinas: virado para o jogador ao ser colocado, aceso enquanto trabalha. */
public class MachineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(Vec3.fromRGB24(0x7A0E14).toVector3f(), 1f);
    private static final DustParticleOptions AMNIOTIC =
            new DustParticleOptions(Vec3.fromRGB24(0x8FD14F).toVector3f(), 0.7f);

    private final MachineKind kind;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(MachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(props -> new MachineBlock(kind, props));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public MachineKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.typeFor(kind).create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.typeFor(kind)) {
            return null;
        }
        return (lvl, pos, st, be) -> ((MachineBlockEntity) be).serverTick((ServerLevel) lvl, st);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            player.openMenu(machine, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.dropContents(level);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Particulas ambiente enquanto a maquina trabalha (somente cliente). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        double frontX = x + facing.getStepX() * 0.52;
        double frontZ = z + facing.getStepZ() * 0.52;
        double side = random.nextDouble() * 0.6 - 0.3;
        double sideX = facing.getAxis() == Direction.Axis.X ? 0 : side;
        double sideZ = facing.getAxis() == Direction.Axis.Z ? 0 : side;

        switch (kind) {
            case BIOMASS_EXTRACTOR -> {
                level.addParticle(BLOOD, frontX + sideX, y + 0.25 + random.nextDouble() * 0.3, frontZ + sideZ,
                        0, 0, 0);
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.4 - 0.2, y + 1.02,
                            z + random.nextDouble() * 0.4 - 0.2, 0, 0.03, 0);
                }
            }
            case GENETIC_CENTRIFUGE -> {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, frontX + sideX, y + 0.5 + side, frontZ + sideZ,
                            facing.getStepX() * 0.05, 0.02, facing.getStepZ() * 0.05);
                }
            }
            case GENE_SEQUENCER -> {
                for (int i = 0; i < 2; i++) {
                    double ox = random.nextDouble() * 2 - 1;
                    double oz = random.nextDouble() * 2 - 1;
                    level.addParticle(ParticleTypes.ENCHANT, x, y + 1.2, z, ox, random.nextDouble() * 0.5 - 0.6, oz);
                }
            }
            case BIOLOGICAL_INCUBATOR -> {
                level.addParticle(AMNIOTIC, x + random.nextDouble() * 0.7 - 0.35, y + 0.25 + random.nextDouble() * 0.5,
                        z + random.nextDouble() * 0.7 - 0.35, 0, 0.02, 0);
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x + random.nextDouble() * 0.5 - 0.25, y + 0.8,
                            z + random.nextDouble() * 0.5 - 0.25, 0, 0.01, 0);
                }
            }
        }
    }
}
