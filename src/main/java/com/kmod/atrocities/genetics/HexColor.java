package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Codec de cor RGB escrita como texto "#RRGGBB" nos JSONs de datapack. */
public final class HexColor {

    private HexColor() {
    }

    public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(HexColor::parse, HexColor::format);

    private static DataResult<Integer> parse(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return DataResult.error(() -> "Cor invalida (use #RRGGBB): " + text);
        }
        try {
            return DataResult.success(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Cor invalida (use #RRGGBB): " + text);
        }
    }

    private static String format(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
