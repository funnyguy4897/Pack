package dev.packmixer;

import dev.packmixer.Library.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class MixerScreen extends Screen {
    static final int PW = 900, PH = 520, LW = 140, RW = 160, HH = 28, FH = 24;
    static final int TXT = 0xFFEDE9FA, DIM = 0xFF9A91B5, GRN = 0xFF4ADE80, GOLD = 0xFFFBBF24;
    record Tex(Identifier id, int w, int h) {}
    static final Map<Path, Tex> TEX = new HashMap<>();

    final Screen parent;
    float sc = 1; int x0, y0, top, bot, cx, cw;
    boolean themes, searching, favOnly, presets, skipChar, hasHover;
    String query = "", toast = ""; long toastAt; int gi, colIdx, colRow, gridRow; Style hover;

    public MixerScreen() { this(null); }
    public MixerScreen(Screen parent) { super(Text.literal("Pack Mixer")); this.parent = parent; if (Library.cols.isEmpty()) Library.init(net.minecraft.client.MinecraftClient.getInstance()); }

    // ---- theme-aware values ----
    static int acc() { return Ui.d.accent; }
    static int accA(int a) { return a << 24 | (Ui.d.accent & 0xFFFFFF); }
    static int R1() { return Ui.d.radius * 2; }
    static int R2() { return Ui.d.radius; }
    static int R3() { return Math.max(0, Ui.d.radius - 1); }

    // ---- layout / state ----
    void layout() {
        sc = Math.max(0.3f, Math.min(Ui.d.scale, Math.min((width - 12f) / PW, (height - 12f) / PH)));
        int vw = (int) (width / sc), vh = (int) (height / sc);
        x0 = (vw - PW) / 2; y0 = (vh - PH) / 2; top = y0 + HH; bot = y0 + PH - FH; cx = x0 + LW; cw = PW - LW - RW;
    }
    List<String> groups() { return Library.cols.stream().map(c -> c.group).distinct().toList(); }
    List<Col> visible() { String g = groups().get(Math.min(gi, groups().size() - 1)); return Library.cols.stream().filter(c -> c.group.equals(g)).toList(); }
    Col cur() { var v = visible(); return v.isEmpty() ? null : v.get(Math.min(colIdx, v.size() - 1)); }
    Style selected(Col c) { String id = Library.sel.get(c.id); return Library.styles.get(c.id).stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
    List<Style> items(Col c) {
        var l = new ArrayList<Style>(); if (query.isEmpty() && !favOnly) l.add(null);
        for (Style s : Library.styles.get(c.id)) if ((!favOnly || Library.favs.contains(c.id + "/" + s.id)) && s.name.toLowerCase().contains(query.toLowerCase())) l.add(s);
        return l;
    }
    int perRow() { return Math.max(1, (cw - 8) / 102); }
    int visRows() { return Math.max(1, (bot - top - 42) / 76); }
    int visCols() { return Math.max(1, (bot - top - 26) / 26); }
    int cardX(int i) { return cx + 8 + (i % perRow()) * 102; }
    int cardY(int i) { return top + 42 + (i / perRow() - gridRow) * 76; }
    boolean cardVis(int i) { int r = i / perRow() - gridRow; return r >= 0 && r < visRows(); }
    int sbx() { return cx + cw - 124; }
    int favX() { return sbx() - 52; }
    int allX() { return favX() - 24; }
    boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    void apply() { Library.dirtyAt = System.currentTimeMillis(); }
    void say(String s) { toast = s; toastAt = System.currentTimeMillis(); }
    void resetView() { colIdx = colRow = gridRow = 0; query = ""; }

    // ---- drawing helpers ----
    static int inset(int r, int i) { double dy = r - i - 0.5; return (int) Math.round(r - Math.sqrt(r * r - dy * dy)); }
    static void rr(DrawContext g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) { int n = inset(r, i); g.fill(x + n, y + i, x + w - n, y + i + 1, c); g.fill(x + n, y + h - 1 - i, x + w - n, y + h - i, c); }
    }
    static void rl(DrawContext g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r == 0) { g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c); }
        for (int i = 0; i < r; i++) {
            int n = inset(r, i), e = i == 0 ? 0 : Math.max(n + 1, inset(r, i - 1));
            for (int yy : new int[]{y + i, y + h - 1 - i}) {
                if (i == 0) g.fill(x + n, yy, x + w - n, yy + 1, c);
                else { g.fill(x + n, yy, x + e, yy + 1, c); g.fill(x + w - e, yy, x + w - n, yy + 1, c); }
            }
        }
        g.fill(x, y + r, x + 1, y + h - r, c); g.fill(x + w - 1, y + r, x + w, y + h - r, c);
    }
    /** Liquid-glass surface: translucent fill, top sheen, thin bright edge. */
    static void glass(DrawContext g, int x, int y, int w, int h, int r, int fill, int line) {
        rr(g, x, y, w, h, r, fill);
        g.fillGradient(x + r, y + 1, x + w - r, y + Math.max(3, h / 2), 0x2AFFFFFF, 0x00FFFFFF);
        rl(g, x, y, w, h, r, line);
    }

    Tex tex(Path p) {
        return TEX.computeIfAbsent(p, k -> {
            try (var in = Files.newInputStream(k)) {
                NativeImage img = NativeImage.read(in);
                Identifier id = Identifier.of("packmixer", "p" + Integer.toHexString(k.toString().hashCode()));
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "packmixer_preview", img));
                return new Tex(id, img.getWidth(), img.getHeight());
            } catch (Exception e) { return new Tex(null, 0, 0); }
        });
    }

    void prev(DrawContext g, Col c, Style s, int x, int y, int w, int h) {
        rr(g, x, y, w, h, R3(), 0x55000000);
        if (s == null) { g.drawCenteredTextWithShadow(textRenderer, "Vanilla", x + w / 2, y + h / 2 - 4, DIM); return; }
        Path p = Library.previewPath(c, s);
        Tex t = p == null ? null : tex(p);
        if (t == null || t.id() == null) { if (w > 40) g.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(c.name, w - 4), x + w / 2, y + h / 2 - 4, DIM); return; }
        float k = Math.min((w - 4f) / t.w(), (h - 4f) / t.h()); int dw = Math.max(1, (int) (t.w() * k)), dh = Math.max(1, (int) (t.h() * k));
        g.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh, t.w(), t.h(), t.w(), t.h());
    }

    void button(DrawContext g, String label, int x, int y, int w, int h, boolean on) {
        glass(g, x, y, w, h, R3(), on ? accA(0x40) : 0x1AFFFFFF, on ? acc() : 0x44FFFFFF);
        g.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, on ? TXT : DIM);
    }

    // ---- render ----
    @Override public void render(DrawContext g, int rmx, int rmy, float d) {
        layout(); int mx = (int) (rmx / sc), my = (int) (rmy / sc); long now = System.currentTimeMillis();
        if (Ui.d.blur || client.world == null) renderBackground(g, rmx, rmy, d); else g.fill(0, 0, width, height, 0x66000000);
        var m = g.getMatrices(); m.pushMatrix(); m.scale(sc, sc);
        glass(g, x0, y0, PW, PH, R1(), Ui.d.alpha << 24 | Ui.d.tint, 0x66FFFFFF);

        // header + loadout (first group = PvP)
        g.drawText(textRenderer, Text.literal("PACK MIXER").formatted(Formatting.BOLD), x0 + 14, y0 + 10, acc(), false);
        g.drawText(textRenderer, "STYLE ARCHIVE", x0 + 92, y0 + 10, DIM, false);
        String g0 = groups().get(0);
        var main = Library.cols.stream().filter(c -> c.group.equals(g0)).limit(14).toList();
        for (int i = 0; i < main.size(); i++) {
            int sx = x0 + PW - 12 - (main.size() - i) * 20; Col c = main.get(i); Style s = selected(c);
            prev(g, c, s, sx, y0 + 5, 18, 18); rl(g, sx, y0 + 5, 18, 18, R3(), s == null ? 0x33FFFFFF : acc());
        }
        g.drawText(textRenderer, "LOADOUT", x0 + PW - 22 - main.size() * 20 - 44, y0 + 10, DIM, false);
        g.fill(x0 + 8, top, x0 + PW - 8, top + 1, 0x22FFFFFF);

        // left: group switcher + collections
        rr(g, x0 + 4, top + 4, LW - 8, bot - top - 8, R2(), 0x14FFFFFF);
        var gs = groups(); gi = Math.min(gi, gs.size() - 1);
        g.drawText(textRenderer, "<", x0 + 12, top + 9, DIM, false); g.drawText(textRenderer, ">", x0 + LW - 18, top + 9, DIM, false);
        g.drawCenteredTextWithShadow(textRenderer, gs.get(gi).toUpperCase(), x0 + LW / 2, top + 9, acc());
        var v = visible(); colRow = Math.max(0, Math.min(colRow, Math.max(0, v.size() - visCols())));
        for (int k = 0; k < visCols() && colRow + k < v.size(); k++) {
            Col c = v.get(colRow + k); int y = top + 24 + k * 26; boolean act = c == cur(); Style s = selected(c);
            if (act) glass(g, x0 + 6, y, LW - 12, 24, R3(), accA(0x30), accA(0x99));
            prev(g, c, s, x0 + 11, y + 4, 16, 16);
            g.drawText(textRenderer, textRenderer.trimToWidth(c.name, 70), x0 + 33, y + 4, act ? TXT : 0xFFC9C2DB, false);
            g.drawText(textRenderer, textRenderer.trimToWidth(s == null ? "Vanilla" : s.name, 70), x0 + 33, y + 14, DIM, false);
            String n = String.valueOf(Library.styles.get(c.id).size()); g.drawText(textRenderer, n, cx - 12 - textRenderer.getWidth(n), y + 8, act ? acc() : DIM, false);
        }
        g.fill(cx, top + 6, cx + 1, bot - 6, 0x22FFFFFF);

        // centre: style grid
        Col c = cur(); hover = null; hasHover = false; Style sel = c == null ? null : selected(c);
        if (c != null) {
            var it = items(c);
            g.drawText(textRenderer, Text.literal(c.name).formatted(Formatting.BOLD), cx + 10, top + 8, TXT, false);
            g.drawText(textRenderer, Library.styles.get(c.id).size() + " styles · " + (sel == null ? "Vanilla" : sel.name), cx + 10, top + 20, DIM, false);
            glass(g, sbx(), top + 6, 114, 18, R3(), 0x22000000, searching ? acc() : 0x44FFFFFF);
            g.drawText(textRenderer, query.isEmpty() && !searching ? "/ Search" : query + (searching ? "_" : ""), sbx() + 6, top + 11, query.isEmpty() ? DIM : TXT, false);
            long fc = Library.favs.stream().filter(s -> s.startsWith(c.id + "/")).count();
            g.drawText(textRenderer, "All", allX(), top + 11, favOnly ? DIM : acc(), false);
            g.drawText(textRenderer, "★ " + fc, favX(), top + 11, favOnly ? acc() : DIM, false);
            int n = perRow(), rows = (it.size() + n - 1) / n; gridRow = Math.max(0, Math.min(gridRow, Math.max(0, rows - visRows())));
            for (int i = 0; i < it.size(); i++) {
                if (!cardVis(i)) continue;
                Style s = it.get(i); int x = cardX(i), y = cardY(i);
                boolean hv = in(mx, my, x, y, 96, 70), on = s == sel;
                if (hv) { hover = s; hasHover = true; }
                glass(g, x, y, 96, 56, R2(), on ? accA(0x30) : hv ? 0x26FFFFFF : 0x12FFFFFF, on ? acc() : hv ? 0x88FFFFFF : 0x33FFFFFF);
                prev(g, c, s, x + 3, y + 3, 90, 50);
                if (on) g.fill(x + 6, y + 6, x + 10, y + 10, GRN);
                if (s != null && Library.favs.contains(c.id + "/" + s.id)) g.drawText(textRenderer, "★", x + 85, y + 5, GOLD, false);
                g.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(s == null ? "Vanilla" : s.name, 92), x + 48, y + 60, on ? TXT : 0xFFC9C2DB);
            }
        }

        // right: inspector / themes
        int rx = x0 + PW - RW; rr(g, rx + 4, top + 4, RW - 8, bot - top - 8, R2(), 0x14FFFFFF);
        g.drawText(textRenderer, "INSPECTOR", rx + 12, top + 9, themes ? DIM : acc(), false);
        g.drawText(textRenderer, "THEMES", rx + 84, top + 9, themes ? acc() : DIM, false);
        if (!themes && c != null) {
            Style f = hasHover ? hover : sel;
            prev(g, c, f, rx + 10, top + 26, RW - 20, 100); rl(g, rx + 10, top + 26, RW - 20, 100, R3(), 0x33FFFFFF);
            g.drawText(textRenderer, Text.literal(f == null ? "Vanilla" : textRenderer.trimToWidth(f.name, RW - 20)).formatted(Formatting.BOLD), rx + 10, top + 134, TXT, false);
            if (f != null) g.drawText(textRenderer, textRenderer.trimToWidth("By " + f.author + (f.builtin ? " · built in" : ""), RW - 20), rx + 10, top + 148, DIM, false);
            g.drawText(textRenderer, "Now: " + textRenderer.trimToWidth(sel == null ? "Vanilla" : sel.name, RW - 50), rx + 10, top + 162, DIM, false);
            button(g, f == sel ? "ACTIVE" : "CLICK CARD TO APPLY", rx + 10, bot - 30, RW - 20, 20, f == sel);
        }
        if (themes) {
            for (int i = 0; i < Ui.PRESETS.length; i++) {
                var p = Ui.PRESETS[i]; int y = top + 26 + i * 18; boolean on = p.accent() == Ui.d.accent && p.tint() == Ui.d.tint;
                glass(g, rx + 8, y, RW - 16, 16, R3(), on ? accA(0x30) : 0x12FFFFFF, on ? acc() : 0x33FFFFFF);
                rr(g, rx + 13, y + 4, 8, 8, 2, p.accent()); g.drawText(textRenderer, p.name(), rx + 27, y + 4, on ? TXT : DIM, false);
            }
            int y = top + 26 + Ui.PRESETS.length * 18 + 10;
            stepper(g, "Opacity", Math.round(Ui.d.alpha * 100 / 255f) + "%", rx, y);
            stepper(g, "Corners", String.valueOf(Ui.d.radius), rx, y + 20);
            stepper(g, "Size", Math.round(Ui.d.scale * 150) + "%", rx, y + 40);
            g.drawText(textRenderer, "Blur", rx + 12, y + 64, DIM, false); button(g, Ui.d.blur ? "ON" : "OFF", rx + RW - 56, y + 60, 44, 16, Ui.d.blur);
        }

        // footer
        g.fill(x0 + 8, bot, x0 + PW - 8, bot + 1, 0x22FFFFFF);
        button(g, "UPLOAD", x0 + 10, bot + 4, 56, 16, false); button(g, "PRESETS", x0 + 70, bot + 4, 56, 16, presets); button(g, "RESET", x0 + 130, bot + 4, 46, 16, false);
        g.drawCenteredTextWithShadow(textRenderer, "CLICK APPLY · F FAVORITE · / SEARCH · DROP .ZIP TO IMPORT", x0 + PW / 2 + 40, bot + 8, DIM);
        g.drawText(textRenderer, "OPEN: K", x0 + PW - 52, bot + 8, DIM, false);
        if (presets) {
            glass(g, x0 + 66, bot - 90, 106, 88, R2(), 0xDD0B0716, 0x66FFFFFF);
            for (int i = 0; i < 5; i++) {
                int y = bot - 84 + i * 16; boolean has = Library.presets.containsKey("" + i);
                button(g, "Preset " + (i + 1) + (has ? " ●" : ""), x0 + 70, y, 62, 14, has); button(g, "Save", x0 + 134, y, 34, 14, false);
            }
        }
        if (now - toastAt < 3500) g.drawCenteredTextWithShadow(textRenderer, toast, x0 + PW / 2, y0 + 34, GRN);
        m.popMatrix();
    }

    void stepper(DrawContext g, String label, String value, int rx, int y) {
        g.drawText(textRenderer, label, rx + 12, y + 4, DIM, false);
        button(g, "-", rx + RW - 62, y, 14, 14, false); g.drawCenteredTextWithShadow(textRenderer, value, rx + RW - 36, y + 3, TXT); button(g, "+", rx + RW - 22, y, 14, 14, false);
    }

    // ---- import ----
    void importAll() {
        int files = 0, parts = 0;
        try (var s = Files.list(Library.IMPORT)) {
            for (Path p : (Iterable<Path>) s::iterator) if (p.toString().toLowerCase().endsWith(".zip")) {
                int r = Library.importZip(p);
                if (r >= 0) { files++; parts += r; Files.createDirectories(Library.IMPORT.resolve("done")); Files.move(p, Library.IMPORT.resolve("done").resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING); }
            }
        } catch (Exception ignored) {}
        if (files == 0) say("Drop a pack .zip here, or put zips in .minecraft/packmixer/import");
        else say("Imported " + files + " pack(s) → " + parts + " components");
    }

    public void onFilesDropped(List<Path> paths) {
        int parts = 0; for (Path p : paths) if (p.toString().toLowerCase().endsWith(".zip")) parts += Math.max(0, Library.importZip(p));
        say(parts > 0 ? "Imported " + parts + " components" : "Drop a resource pack .zip");
    }

    // ---- input ----
    @Override public boolean mouseClicked(Click click, boolean doubled) { layout(); return click(click.x() / sc, click.y() / sc, click.button()) || super.mouseClicked(click, doubled); }

    boolean click(double mx, double my, int b) {
        Col c = cur(); int rx = x0 + PW - RW;
        if (in(mx, my, x0 + 10, bot + 4, 56, 16)) { importAll(); return true; }
        if (in(mx, my, x0 + 70, bot + 4, 56, 16)) { presets = !presets; return true; }
        if (in(mx, my, x0 + 130, bot + 4, 46, 16)) { Library.sel.clear(); Library.save(); apply(); return true; }
        if (presets) for (int i = 0; i < 5; i++) {
            int y = bot - 84 + i * 16;
            if (in(mx, my, x0 + 70, y, 62, 14) && Library.presets.containsKey("" + i)) { Library.sel = new HashMap<>(Library.presets.get("" + i)); Library.save(); apply(); return true; }
            if (in(mx, my, x0 + 134, y, 34, 14)) { Library.presets.put("" + i, new HashMap<>(Library.sel)); Library.save(); say("Saved preset " + (i + 1)); return true; }
        }
        // right tabs + themes
        if (in(mx, my, rx, top, RW, 20)) { themes = mx >= rx + 80; return true; }
        if (themes && in(mx, my, rx, top + 20, RW, bot - top - 20)) {
            for (int i = 0; i < Ui.PRESETS.length; i++) if (in(mx, my, rx + 8, top + 26 + i * 18, RW - 16, 16)) { var p = Ui.PRESETS[i]; Ui.d.accent = p.accent(); Ui.d.tint = p.tint(); Ui.d.alpha = p.alpha(); Ui.save(); return true; }
            int y = top + 26 + Ui.PRESETS.length * 18 + 10;
            for (int k = 0; k < 3; k++) for (int sgn = -1; sgn <= 1; sgn += 2) if (in(mx, my, rx + RW + (sgn < 0 ? -62 : -22), y + k * 20, 14, 14)) {
                if (k == 0) Ui.d.alpha = Math.max(0x20, Math.min(0xF0, Ui.d.alpha + sgn * 0x11));
                if (k == 1) Ui.d.radius = Math.max(0, Math.min(8, Ui.d.radius + sgn));
                if (k == 2) Ui.d.scale = Math.max(0.4f, Math.min(1f, Math.round((Ui.d.scale + sgn * 0.05f) * 100) / 100f));
                Ui.save(); return true;
            }
            if (in(mx, my, rx + RW - 56, y + 60, 44, 16)) { Ui.d.blur = !Ui.d.blur; Ui.save(); return true; }
            return true;
        }
        // group switcher + collection rows
        if (in(mx, my, x0, top, LW, 22)) { int n = groups().size(); gi = (gi + (mx < x0 + LW / 2 ? -1 : 1) + n) % n; resetView(); return true; }
        var v = visible();
        for (int k = 0; k < visCols() && colRow + k < v.size(); k++) if (in(mx, my, x0 + 4, top + 24 + k * 26, LW - 8, 26)) { colIdx = colRow + k; gridRow = 0; return true; }
        if (c == null) return false;
        searching = in(mx, my, sbx(), top + 6, 114, 18);
        if (in(mx, my, favX(), top + 6, 40, 18)) favOnly = true;
        if (in(mx, my, allX(), top + 6, 20, 18)) favOnly = false;
        var it = items(c);
        for (int i = 0; i < it.size(); i++) if (cardVis(i) && in(mx, my, cardX(i), cardY(i), 96, 70)) {
            Style s = it.get(i);
            if (b == 0) { if (s == null) Library.sel.remove(c.id); else Library.sel.put(c.id, s.id); Library.save(); apply(); }
            else if (s != null) fav(c, s);
            return true;
        }
        return false;
    }

    void fav(Col c, Style s) { String k = c.id + "/" + s.id; if (!Library.favs.remove(k)) Library.favs.add(k); Library.save(); }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        layout(); int dir = v > 0 ? -1 : 1;
        if (mx / sc < cx) colRow = Math.max(0, Math.min(colRow + dir, Math.max(0, visible().size() - visCols())));
        else { Col c = cur(); if (c != null) { int rows = (items(c).size() + perRow() - 1) / perRow(); gridRow = Math.max(0, Math.min(gridRow + dir, Math.max(0, rows - visRows()))); } }
        return true;
    }

    @Override public boolean keyPressed(KeyInput in) { return key(in.key()) || super.keyPressed(in); }

    boolean key(int k) {
        if (searching) {
            if (k == GLFW_KEY_BACKSPACE && !query.isEmpty()) query = query.substring(0, query.length() - 1);
            else if (k == GLFW_KEY_ENTER || k == GLFW_KEY_ESCAPE) searching = false;
            return true;
        }
        Col c = cur();
        if (k == GLFW_KEY_SLASH) { searching = skipChar = true; return true; }
        if (k == GLFW_KEY_F && hasHover && hover != null && c != null) { fav(c, hover); return true; }
        if (k == GLFW_KEY_K) { close(); return true; }
        return false;
    }

    @Override public boolean charTyped(CharInput in) {
        if (!searching) return false;
        if (skipChar) { skipChar = false; return true; }
        int cp = in.codepoint();
        if (cp >= 32 && query.length() < 24) { query += Character.toString(cp); gridRow = 0; }
        return true;
    }

    @Override public void close() { client.setScreen(parent); }
}
