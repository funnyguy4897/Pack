package dev.packmixer;

/** Customisable look of the Pack Mixer screen (saved to packmixer/theme.json). */
final class Ui {
    static class D { int accent = 0xFFB59CFF, tint = 0x0B0716, alpha = 0x99, radius = 4; float scale = 2f / 3f; boolean blur = true; }
    record Preset(String name, int accent, int tint, int alpha) {}
    static final Preset[] PRESETS = {
        new Preset("Liquid Glass", 0xFFB59CFF, 0x0B0716, 0x99), new Preset("Clear Glass", 0xFFE5E7EB, 0x10141C, 0x55),
        new Preset("Ocean", 0xFF5EEAD4, 0x04141A, 0x99), new Preset("Rose", 0xFFF472B6, 0x180510, 0x99),
        new Preset("Ember", 0xFFFB923C, 0x160A04, 0x99), new Preset("Emerald", 0xFF4ADE80, 0x04140A, 0x99),
        new Preset("Midnight", 0xFF60A5FA, 0x050A18, 0xCC), new Preset("Mono", 0xFFE5E7EB, 0x0A0A0A, 0xB0)};
    static D d = new D();
    static void load() { d = Library.read("theme.json", D.class, new D()); }
    static void save() { Library.write(Library.ROOT.resolve("theme.json"), d); }
}
