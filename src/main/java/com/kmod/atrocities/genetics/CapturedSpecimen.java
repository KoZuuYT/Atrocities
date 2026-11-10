package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;

/**
 * Data Component da Capsula de Estase cheia: um especime vivo guardado inteiro.
 * O estado completo da entidade (equipamento, efeitos, nome) fica em {@code entityData},
 * pois so o proprio Minecraft sabe serializar uma entidade.
 *
 * @param species    especie do especime
 * @param entityType tipo da entidade (para exibir o nome mesmo sem a especie no datapack)
 * @param entityData estado salvo da entidade
 * @param health     vida no momento da captura
 * @param maxHealth  vida maxima no momento da captura
 */
public record CapturedSpecimen(ResourceKey<Species> species, EntityType<?> entityType, CustomData entityData,
                               float health, float maxHealth) {

    public static final Codec<CapturedSpecimen> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(GeneticsRegistries.SPECIES).fieldOf("species").forGetter(CapturedSpecimen::species),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(CapturedSpecimen::entityType),
            CustomData.CODEC.fieldOf("entity_data").forGetter(CapturedSpecimen::entityData),
            Codec.FLOAT.fieldOf("health").forGetter(CapturedSpecimen::health),
            Codec.FLOAT.fieldOf("max_health").forGetter(CapturedSpecimen::maxHealth)
    ).apply(i, CapturedSpecimen::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedSpecimen> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(GeneticsRegistries.SPECIES), CapturedSpecimen::species,
            ByteBufCodecs.registry(Registries.ENTITY_TYPE), CapturedSpecimen::entityType,
            CustomData.STREAM_CODEC, CapturedSpecimen::entityData,
            ByteBufCodecs.FLOAT, CapturedSpecimen::health,
            ByteBufCodecs.FLOAT, CapturedSpecimen::maxHealth,
            CapturedSpecimen::new);

    public float healthRatio() {
        return maxHealth > 0 ? health / maxHealth : 0f;
    }
}
