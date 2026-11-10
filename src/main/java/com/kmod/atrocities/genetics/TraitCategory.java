package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Grupo funcional de um traco genetico. */
public enum TraitCategory implements StringRepresentable {
    OFFENSE("offense", 0xD9534F),
    DEFENSE("defense", 0x8FA3B8),
    MOBILITY("mobility", 0xE0B040),
    SPECIAL("special", 0xB07CE0);

    public static final Codec<TraitCategory> CODEC = StringRepresentable.fromEnum(TraitCategory::values);

    private final String name;
    private final int color;

    TraitCategory(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("trait_category.atrocities." + name).withColor(color);
    }
}
