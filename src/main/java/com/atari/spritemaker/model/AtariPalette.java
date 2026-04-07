package com.atari.spritemaker.model;

import java.awt.Color;

public class AtariPalette {

    public enum Region {
        NTSC, PAL, SECAM
    }

    public static Color[] getColors(Region region) {
        switch (region) {
            case PAL:    return buildColors(PAL_RGB);
            case SECAM:  return buildColors(SECAM_RGB);
            default:     return buildColors(NTSC_RGB);
        }
    }

    private static Color[] buildColors(int[] rgb) {
        Color[] colors = new Color[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            colors[i] = new Color(rgb[i]);
        }
        return colors;
    }

    // -------------------------------------------------------------------------
    // NTSC — 128 colors (16 hues × 8 luminance levels)
    // -------------------------------------------------------------------------
    private static final int[] NTSC_RGB = {
        // Hue  0: Grayscale
        0x000000, 0x404040, 0x6C6C6C, 0x909090, 0xB0B0B0, 0xC8C8C8, 0xDCDCDC, 0xECECEC,
        // Hue  1: Gold
        0x444400, 0x646410, 0x848424, 0xA0A034, 0xB8B840, 0xD0D050, 0xE8E85C, 0xFCFC68,
        // Hue  2: Orange-Gold
        0x702800, 0x844414, 0x985C28, 0xAC783C, 0xBC8C4C, 0xCCA05C, 0xDCB468, 0xECC878,
        // Hue  3: Orange
        0x841800, 0x983418, 0xAC5030, 0xC06848, 0xD0805C, 0xE09470, 0xECA880, 0xFCBC94,
        // Hue  4: Red
        0x880000, 0x9C2020, 0xB03C3C, 0xC05858, 0xD07070, 0xE08888, 0xECA0A0, 0xFCB4B4,
        // Hue  5: Pink
        0x78005C, 0x8C2074, 0xA03C88, 0xB0589C, 0xC070B0, 0xD084C0, 0xDC9CD0, 0xECB0E0,
        // Hue  6: Purple
        0x480078, 0x602090, 0x783CA4, 0x8C58B8, 0xA070CC, 0xB484DC, 0xC49CEC, 0xD4B0FC,
        // Hue  7: Purple-Blue
        0x140090, 0x3020A4, 0x4C3CB8, 0x6858CC, 0x7C70DC, 0x9488EC, 0xA8A0FC, 0xBCB4FC,
        // Hue  8: Blue
        0x000094, 0x181CA8, 0x3034BC, 0x4C50CC, 0x6468DC, 0x7C80EC, 0x9498FC, 0xACB0FC,
        // Hue  9: Blue-Teal
        0x001C88, 0x18389C, 0x3050B0, 0x4C68C4, 0x6480D4, 0x8098E4, 0x98B0F4, 0xB0C8FC,
        // Hue 10: Teal
        0x003870, 0x185484, 0x347098, 0x4C8CAC, 0x64A4BC, 0x7CBCCC, 0x94D0DC, 0xACE4EC,
        // Hue 11: Sea Green
        0x003C38, 0x1C5850, 0x347868, 0x4C9480, 0x64AC94, 0x7CC4A8, 0x94D8BC, 0xACECD0,
        // Hue 12: Green
        0x003800, 0x205418, 0x3C7034, 0x588C50, 0x6CA468, 0x84BC80, 0x9CD094, 0xB4E4AC,
        // Hue 13: Yellow-Green
        0x143400, 0x345018, 0x4C6C30, 0x688848, 0x809C5C, 0x9CB870, 0xB4CC84, 0xCCE09C,
        // Hue 14: Yellow
        0x2C3000, 0x4C4C00, 0x686818, 0x848430, 0x9C9C44, 0xB8B858, 0xD0D070, 0xE8E884,
        // Hue 15: Yellow-Orange
        0x442C00, 0x644818, 0x846428, 0xA08040, 0xB89854, 0xD0B068, 0xE8C87C, 0xFCE090,
    };

    // -------------------------------------------------------------------------
    // PAL — 104 colors (13 hues × 8 luminance levels)
    // -------------------------------------------------------------------------
    private static final int[] PAL_RGB = {
        // Hue  0: Grayscale
        0x000000, 0x282828, 0x505050, 0x747474, 0x989898, 0xBCBCBC, 0xDCDCDC, 0xECECEC,
        // Hue  1: Yellow
        0x484800, 0x6C6C1C, 0x909038, 0xB4B454, 0xD4D470, 0xF0F088, 0xFCFCA0, 0xFCFCB8,
        // Hue  2: Orange
        0x7C3400, 0xA05020, 0xC4703C, 0xE48C58, 0xFCA874, 0xFCC090, 0xFCD4A8, 0xFCE8C0,
        // Hue  3: Red-Orange
        0x8C2000, 0xB04020, 0xD4603C, 0xEC7C58, 0xFC9874, 0xFCB490, 0xFCCCA8, 0xFCE0C0,
        // Hue  4: Pink-Red
        0x800038, 0xA02054, 0xC0406C, 0xDC5C84, 0xF47CA0, 0xFC98BC, 0xFCB0D0, 0xFCC8E4,
        // Hue  5: Purple
        0x5C0068, 0x802088, 0xA440A8, 0xC460C4, 0xE080E0, 0xF89CF8, 0xFCB8FC, 0xFCD0FC,
        // Hue  6: Blue-Purple
        0x280090, 0x4C20B4, 0x6C44D4, 0x8C68F4, 0xAC8CFC, 0xC4ACFC, 0xDCCCFC, 0xECE4FC,
        // Hue  7: Blue
        0x0000A8, 0x2020CC, 0x4040EC, 0x6464FC, 0x8888FC, 0xACACFC, 0xCCCCFC, 0xE4E4FC,
        // Hue  8: Light Blue
        0x00209C, 0x2044BC, 0x4068DC, 0x6090F4, 0x80B4FC, 0xA0D0FC, 0xC0E4FC, 0xDCF4FC,
        // Hue  9: Cyan
        0x005488, 0x2078AC, 0x409CC8, 0x60C0E8, 0x84E0FC, 0xA4F4FC, 0xC4FCFC, 0xDCFCFC,
        // Hue 10: Sea Green
        0x005838, 0x207C5C, 0x40A07C, 0x64C49C, 0x84E4BC, 0xA4F8D8, 0xC4FCF0, 0xDCFCFC,
        // Hue 11: Green
        0x004800, 0x206C24, 0x40904C, 0x64B470, 0x88D898, 0xACF4BC, 0xC8FCD4, 0xE4FCEC,
        // Hue 12: Yellow-Green
        0x2C4000, 0x4C6020, 0x6C843C, 0x90A85C, 0xB4CC7C, 0xD4EC9C, 0xECFCBC, 0xFCFCD4,
    };

    // -------------------------------------------------------------------------
    // SECAM — 8 fixed hardware colors
    // -------------------------------------------------------------------------
    private static final int[] SECAM_RGB = {
        0x000000, // Black
        0x2121FF, // Blue
        0xF03C79, // Red
        0xFF50FF, // Magenta
        0x7FFF00, // Green
        0x7FFFFF, // Cyan
        0xFFFF3F, // Yellow
        0xFFFFFF, // White
    };
}
