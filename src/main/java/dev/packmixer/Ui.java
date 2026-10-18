package dev.packmixer;

import java.awt.Color;

/** Look of the Pack Mixer screen: a pickable STYLE plus any COLOR (saved to packmixer/theme.json). */
final class Ui {
    static class D { float h = 0.73f, s = 0.39f, v = 1f; int style = 0, alpha = 0x99, radius = 6; float scale = 2f / 3f; boolean blur = true; }
    static final String[] STYLES = {"Liquid", "Frosted", "Clear", "Solid", "Neon"};
    static final int[] SWATCHES = {0xB59CFF, 0x5EEAD4, 0xF472B6, 0xFB923C, 0x4ADE80, 0x60A5FA, 0xFACC15, 0xE5E7EB};
    static D d = new D();
    static int color() { return Color.HSBtoRGB(d.h, d.s, d.v) & 0xFFFFFF; }
    static void setRgb(int rgb) { float[] f = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null); d.h = f[0]; d.s = f[1]; d.v = f[2]; }
    static void load() { d = Library.read("theme.json", D.class, new D()); }
    static void save() { Library.write(Library.ROOT.resolve("theme.json"), d); }
}
