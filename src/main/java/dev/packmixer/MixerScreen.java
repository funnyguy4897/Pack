package dev.packmixer;

import dev.packmixer.Library.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.nio.file.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class MixerScreen extends Screen {
    static final int PW = 900, PH = 520, LW = 140, RW = 160, HH = 28, FH = 24;
    static final int TXT = 0xFFEDE9FA, DIM = 0xFF9A91B5, GRN = 0xFF4ADE80, GOLD = 0xFFFBBF24;
    record Tex(Identifier id, int w, int h) {}
    static final Map<Path, Tex> TEX = new HashMap<>();

    final Screen parent;
    float sc = 1; int x0, y0, top, bot, cx, cw, drag;
    boolean themes, searching, favOnly, presets, skipChar, hasHover, pushed;
    String query = "", toast = ""; long toastAt; int gi, colIdx, colRow, gridRow; Style hover; Throwable error;

    public MixerScreen() { this(null); }
    public MixerScreen(Screen parent) { super(Component.literal("Pack Mixer")); this.parent = parent; if (Library.cols.isEmpty()) Library.init(net.minecraft.client.Minecraft.getInstance()); }

    // ---- theme values ----
    static int acc() { return 0xFF000000 | Ui.color(); }
    static int R1() { return Math.min(24, Ui.d.radius * 2); }
    static int R2() { return Ui.d.radius; }
    static int R3() { return Math.max(0, Ui.d.radius - 2); }

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

    // ---- colour helpers ----
    static int lerp(int a, int b, float t) { int r = 0; for (int s = 24; s >= 0; s -= 8) { int ca = a >>> s & 255, cb = b >>> s & 255; r |= (int) (ca + (cb - ca) * t) << s; } return r; }
    static int mul(int rgb, float f) { return Math.min(255, (int) ((rgb >> 16 & 255) * f)) << 16 | Math.min(255, (int) ((rgb >> 8 & 255) * f)) << 8 | Math.min(255, (int) ((rgb & 255) * f)); }
    static int fade(int c, float k) { return (int) ((c >>> 24) * k) << 24 | c & 0xFFFFFF; }

    // ---- shapes ----
    static int inset(int r, int i) { double dy = r - i - 0.5; return (int) Math.round(r - Math.sqrt(r * r - dy * dy)); }
    /** Rounded rectangle with a vertical gradient (cT at top, cB at bottom). */
    static void rrg(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int cT, int cB) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int n = inset(r, i);
            g.fill(x + n, y + i, x + w - n, y + i + 1, lerp(cT, cB, (i + .5f) / h));
            g.fill(x + n, y + h - 1 - i, x + w - n, y + h - i, lerp(cT, cB, (h - 1 - i + .5f) / h));
        }
        g.fillGradient(x, y + r, x + w, y + h - r, lerp(cT, cB, (float) r / h), lerp(cT, cB, (float) (h - r) / h));
    }
    static void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) { rrg(g, x, y, w, h, r, c, c); }
    /** Rounded 1px outline, colour fading from cT (top) to cB (bottom). */
    static void rl(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int cT, int cB) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r == 0) { g.fill(x, y, x + w, y + 1, cT); g.fill(x, y + h - 1, x + w, y + h, cB); }
        for (int i = 0; i < r; i++) {
            int n = inset(r, i), e = i == 0 ? 0 : Math.max(n + 1, inset(r, i - 1));
            for (int yy : new int[]{y + i, y + h - 1 - i}) {
                int c = lerp(cT, cB, (yy - y + .5f) / h);
                if (i == 0) g.fill(x + n, yy, x + w - n, yy + 1, c);
                else { g.fill(x + n, yy, x + e, yy + 1, c); g.fill(x + w - e, yy, x + w - n, yy + 1, c); }
            }
        }
        int c1 = lerp(cT, cB, (float) r / h), c2 = lerp(cT, cB, (float) (h - r) / h);
        g.fillGradient(x, y + r, x + 1, y + h - r, c1, c2); g.fillGradient(x + w - 1, y + r, x + w, y + h - r, c1, c2);
    }
    static void rl(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) { rl(g, x, y, w, h, r, c, c); }

    /** Draws a surface in the chosen STYLE and COLOR. lvl: 0 main panel, 1 inset area, 2 control, 3 hovered control, 4 active control, 5 popup. */
    static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int lvl) {
        int col = Ui.color(), al = Ui.d.alpha, st = Ui.d.style, A = 0xFF000000 | col;
        boolean act = lvl == 4, hov = lvl == 3, sheen = false, inner = false, glow = false; int fT, fB, rT = 0, rB = 0;
        if (lvl == 0) {
            int t = mul(col, st == 3 ? 0.18f : st == 1 ? 0.25f : 0.4f);
            switch (st) {
                case 0 -> { fT = al * 9 / 10 << 24 | t; fB = al * 55 / 100 << 24 | t; rT = 0x99FFFFFF; rB = 0x33FFFFFF; sheen = inner = true; }
                case 1 -> { fT = fB = al << 24 | t; rT = rB = 0x40FFFFFF; }
                case 2 -> { fT = fB = al / 3 << 24 | t; rT = 0xB0FFFFFF; rB = 0x55FFFFFF; inner = true; }
                case 3 -> { fT = fB = 0xFF000000 | t; rT = rB = 0x55FFFFFF; }
                default -> { fT = fB = al << 24 | t; rT = rB = 0xB0000000 | col; glow = true; }
            }
        } else if (lvl == 1) { fT = fB = st == 3 ? 0xFF000000 | mul(col, 0.3f) : 0x12FFFFFF; if (st == 0) fB = 0x08FFFFFF; }
        else if (lvl == 5) { fT = fB = 0xEE000000 | mul(col, 0.25f); rT = rB = 0x66FFFFFF; }
        else switch (st) {
            case 0 -> { fT = act ? 0x9A000000 | col : hov ? 0x46FFFFFF : 0x32FFFFFF; fB = act ? 0x50000000 | col : hov ? 0x1CFFFFFF : 0x0CFFFFFF;
                        rT = act ? A : hov ? 0xE6FFFFFF : 0xA6FFFFFF; rB = act ? 0x80000000 | col : 0x44FFFFFF; sheen = inner = true; }
            case 1 -> { fT = fB = act ? 0x50000000 | col : hov ? 0x26FFFFFF : 0x14FFFFFF; rT = rB = act ? A : hov ? 0x66FFFFFF : 0x33FFFFFF; }
            case 2 -> { fT = fB = act ? 0x30000000 | col : hov ? 0x16FFFFFF : 0x0AFFFFFF; rT = act ? A : hov ? 0xE6FFFFFF : 0xA6FFFFFF; rB = act ? 0x90000000 | col : 0x50FFFFFF; }
            case 3 -> { fT = fB = 0xFF000000 | mul(col, act ? 0.7f : hov ? 0.38f : 0.3f); rT = rB = act ? A : 0xFF000000 | mul(col, 0.6f); }
            default -> { fT = fB = act ? 0x38000000 | col : hov ? 0x1CFFFFFF : 0x0AFFFFFF; rT = rB = act ? A : hov ? 0xC0000000 | col : 0x66000000 | col; glow = act || hov; }
        }
        rrg(g, x, y, w, h, r, fT, fB);
        if (sheen) g.fillGradient(x + r, y + 1, x + w - r, y + Math.max(3, h / 2), lvl == 0 ? 0x1AFFFFFF : 0x30FFFFFF, 0x00FFFFFF);
        if (rT != 0 || rB != 0) rl(g, x, y, w, h, r, rT, rB);
        if (inner && w > 6 && h > 6) rl(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fade(rT, .3f), fade(rB, .3f));
        if (glow) rl(g, x - 1, y - 1, w + 2, h + 2, r + 1, 0x30000000 | col);
    }

    Tex tex(Path p) {
        return TEX.computeIfAbsent(p, k -> {
            try (var in = Files.newInputStream(k)) {
                NativeImage img = NativeImage.read(in);
                Identifier id = Identifier.fromNamespaceAndPath("packmixer", "p" + Integer.toHexString(k.toString().hashCode()));
                minecraft.getTextureManager().register(id, new DynamicTexture(() -> "packmixer_preview", img));
                return new Tex(id, img.getWidth(), img.getHeight());
            } catch (Exception e) { return new Tex(null, 0, 0); }
        });
    }

    /** Preview thumbnail. Small slots never contain text, so nothing can overflow or overlap. */
    void prev(GuiGraphicsExtractor g, Col c, Style s, int x, int y, int w, int h) {
        rr(g, x, y, w, h, R3(), 0x55000000);
        boolean big = w >= 40;
        if (s == null) { if (big) g.centeredText(font, "Vanilla", x + w / 2, y + h / 2 - 4, DIM); return; }
        Path p = Library.previewPath(c, s);
        Tex t = p == null ? null : tex(p);
        if (t == null || t.id() == null) { if (big) g.centeredText(font, font.plainSubstrByWidth(c.name, w - 4), x + w / 2, y + h / 2 - 4, DIM); return; }
        float k = Math.min((w - 4f) / t.w(), (h - 4f) / t.h()); int dw = Math.max(1, (int) (t.w() * k)), dh = Math.max(1, (int) (t.h() * k));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh, t.w(), t.h(), t.w(), t.h());
    }

    void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean on) {
        glass(g, x, y, w, h, R3(), on ? 4 : 2);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, on ? TXT : DIM);
    }

    // ---- render ----
    /** Never crashes the game: any drawing error is shown on screen (and printed to the log) instead. */
    @Override public void extractRenderState(GuiGraphicsExtractor g, int rmx, int rmy, float d) {
        try { if (error == null) draw(g, rmx, rmy, d); }
        catch (Throwable t) { error = t; t.printStackTrace(); if (pushed) { pushed = false; try { g.pose().popMatrix(); } catch (Throwable ignored) {} } }
        if (error != null) {
            g.fill(0, 0, width, height, 0xCC000000);
            g.centeredText(font, "Pack Mixer hit an error (press Esc). Please report this:", width / 2, height / 2 - 20, 0xFFFF8080);
            g.centeredText(font, String.valueOf(error), width / 2, height / 2 - 6, 0xFFFFFFFF);
            StackTraceElement[] st = error.getStackTrace();
            for (int i = 0; i < Math.min(3, st.length); i++) g.centeredText(font, st[i].toString(), width / 2, height / 2 + 10 + i * 11, 0xFFC9C2DB);
        }
    }

    /** Another mod may already have blurred this frame (the game only allows one blur), so fall back to plain darkening. */
    void backdrop(GuiGraphicsExtractor g, int rmx, int rmy, float d) {
        try { if (Ui.d.blur || minecraft.level == null) super.extractRenderState(g, rmx, rmy, d); else g.fill(0, 0, width, height, 0x66000000); }
        catch (IllegalStateException e) { g.fill(0, 0, width, height, 0x66000000); }
    }

    void draw(GuiGraphicsExtractor g, int rmx, int rmy, float d) {
        layout(); int mx = (int) (rmx / sc), my = (int) (rmy / sc); long now = System.currentTimeMillis();
        backdrop(g, rmx, rmy, d);
        var m = g.pose(); m.pushMatrix(); pushed = true; m.scale(sc, sc);
        glass(g, x0, y0, PW, PH, R1(), 0);

        // header + loadout (first group)
        g.text(font, Component.literal("PACK MIXER").withStyle(ChatFormatting.BOLD), x0 + 16, y0 + 10, acc(), false);
        g.text(font, "STYLE ARCHIVE", x0 + 94, y0 + 10, DIM, false);
        String g0 = groups().get(0);
        var main = Library.cols.stream().filter(c -> c.group.equals(g0)).limit(14).toList();
        for (int i = 0; i < main.size(); i++) {
            int sx = x0 + PW - 16 - (main.size() - i) * 21; Col c = main.get(i); Style s = selected(c);
            prev(g, c, s, sx, y0 + 5, 18, 18); rl(g, sx, y0 + 5, 18, 18, R3(), s == null ? 0x33FFFFFF : acc());
        }
        g.text(font, "LOADOUT", x0 + PW - 26 - main.size() * 21 - 44, y0 + 10, DIM, false);
        g.fill(x0 + 10, top, x0 + PW - 10, top + 1, 0x22FFFFFF);

        // left: group switcher + collections
        glass(g, x0 + 6, top + 6, LW - 12, bot - top - 12, R2(), 1);
        var gs = groups(); gi = Math.min(gi, gs.size() - 1);
        g.text(font, "<", x0 + 16, top + 12, DIM, false); g.text(font, ">", x0 + LW - 22, top + 12, DIM, false);
        g.centeredText(font, gs.get(gi).toUpperCase(), x0 + LW / 2, top + 12, acc());
        var v = visible(); colRow = Math.max(0, Math.min(colRow, Math.max(0, v.size() - visCols())));
        for (int k = 0; k < visCols() && colRow + k < v.size(); k++) {
            Col c = v.get(colRow + k); int y = top + 26 + k * 26; boolean act = c == cur(); Style s = selected(c);
            if (act) glass(g, x0 + 9, y, LW - 18, 24, R3(), 4);
            prev(g, c, s, x0 + 14, y + 4, 16, 16);
            g.text(font, font.plainSubstrByWidth(c.name, 66), x0 + 36, y + 4, act ? TXT : 0xFFC9C2DB, false);
            g.text(font, font.plainSubstrByWidth(s == null ? "Vanilla" : s.name, 66), x0 + 36, y + 14, DIM, false);
            String n = String.valueOf(Library.styles.get(c.id).size()); g.text(font, n, cx - 16 - font.width(n), y + 8, act ? acc() : DIM, false);
        }

        // centre: style grid
        Col c = cur(); hover = null; hasHover = false; Style sel = c == null ? null : selected(c);
        if (c != null) {
            var it = items(c);
            g.text(font, Component.literal(c.name).withStyle(ChatFormatting.BOLD), cx + 10, top + 8, TXT, false);
            g.text(font, Library.styles.get(c.id).size() + " styles · " + (sel == null ? "Vanilla" : sel.name), cx + 10, top + 20, DIM, false);
            glass(g, sbx(), top + 6, 114, 18, R3(), searching ? 4 : 2);
            g.text(font, query.isEmpty() && !searching ? "/ Search" : query + (searching ? "_" : ""), sbx() + 7, top + 11, query.isEmpty() ? DIM : TXT, false);
            long fc = Library.favs.stream().filter(s -> s.startsWith(c.id + "/")).count();
            g.text(font, "All", allX(), top + 11, favOnly ? DIM : acc(), false);
            g.text(font, "★ " + fc, favX(), top + 11, favOnly ? acc() : DIM, false);
            int n = perRow(), rows = (it.size() + n - 1) / n; gridRow = Math.max(0, Math.min(gridRow, Math.max(0, rows - visRows())));
            for (int i = 0; i < it.size(); i++) {
                if (!cardVis(i)) continue;
                Style s = it.get(i); int x = cardX(i), y = cardY(i);
                boolean hv = in(mx, my, x, y, 96, 70), on = s == sel;
                if (hv) { hover = s; hasHover = true; }
                glass(g, x, y, 96, 56, R2(), on ? 4 : hv ? 3 : 2);
                prev(g, c, s, x + 4, y + 4, 88, 48);
                if (on) g.fill(x + 7, y + 7, x + 11, y + 11, GRN);
                if (s != null && Library.favs.contains(c.id + "/" + s.id)) g.text(font, "★", x + 84, y + 6, GOLD, false);
                g.centeredText(font, font.plainSubstrByWidth(s == null ? "Vanilla" : s.name, 92), x + 48, y + 60, on ? TXT : 0xFFC9C2DB);
            }
        }

        // right: inspector / themes
        int rx = x0 + PW - RW; glass(g, rx + 4, top + 6, RW - 10, bot - top - 12, R2(), 1);
        g.text(font, "INSPECTOR", rx + 14, top + 12, themes ? DIM : acc(), false);
        g.text(font, "THEMES", rx + 86, top + 12, themes ? acc() : DIM, false);
        if (!themes && c != null) {
            Style f = hasHover ? hover : sel;
            prev(g, c, f, rx + 12, top + 28, RW - 24, 100); rl(g, rx + 12, top + 28, RW - 24, 100, R3(), 0x33FFFFFF);
            g.text(font, Component.literal(f == null ? "Vanilla" : font.plainSubstrByWidth(f.name, RW - 24)).withStyle(ChatFormatting.BOLD), rx + 12, top + 136, TXT, false);
            if (f != null) g.text(font, font.plainSubstrByWidth("By " + f.author + (f.builtin ? " · built in" : ""), RW - 24), rx + 12, top + 150, DIM, false);
            g.text(font, "Now: " + font.plainSubstrByWidth(sel == null ? "Vanilla" : sel.name, RW - 54), rx + 12, top + 164, DIM, false);
            button(g, f == sel ? "ACTIVE" : "CLICK CARD TO APPLY", rx + 12, bot - 32, RW - 24, 20, f == sel);
        }
        if (themes) drawThemes(g, rx);

        // footer
        g.fill(x0 + 10, bot, x0 + PW - 10, bot + 1, 0x22FFFFFF);
        button(g, "UPLOAD", x0 + 12, bot + 4, 56, 16, false); button(g, "PRESETS", x0 + 72, bot + 4, 56, 16, presets); button(g, "RESET", x0 + 132, bot + 4, 46, 16, false);
        g.centeredText(font, "CLICK APPLY · F FAVORITE · / SEARCH · DROP .ZIP TO IMPORT", x0 + PW / 2 + 40, bot + 8, DIM);
        g.text(font, "OPEN: K", x0 + PW - 56, bot + 8, DIM, false);
        if (presets) {
            glass(g, x0 + 68, bot - 92, 106, 90, R2(), 5);
            for (int i = 0; i < 5; i++) {
                int y = bot - 86 + i * 16; boolean has = Library.presets.containsKey("" + i);
                button(g, "Preset " + (i + 1) + (has ? " ●" : ""), x0 + 72, y, 62, 14, has); button(g, "Save", x0 + 136, y, 34, 14, false);
            }
        }
        if (now - toastAt < 3500) g.centeredText(font, toast, x0 + PW / 2, y0 + 34, GRN);
        m.popMatrix(); pushed = false;
    }

    // THEMES tab layout (shared by drawing and clicking): STYLE rows, COLOR picker, steppers
    int tY() { return top + 28; }
    int tColorY() { return tY() + 12 + Ui.STYLES.length * 17 + 8; }
    int tSteps() { return tColorY() + 12 + 56 + 6 + 16 + 24; }

    void drawThemes(GuiGraphicsExtractor g, int rx) {
        int y = tY();
        g.text(font, "STYLE", rx + 14, y, DIM, false);
        for (int i = 0; i < Ui.STYLES.length; i++) {
            int yy = y + 12 + i * 17; boolean on = Ui.d.style == i;
            glass(g, rx + 10, yy, RW - 24, 15, R3(), on ? 4 : 2);
            g.text(font, Ui.STYLES[i], rx + 18, yy + 4, on ? TXT : DIM, false);
        }
        int cy = tColorY(), sx = rx + 10, sy = cy + 12, sw = 140, sh = 56;
        String hex = String.format("#%06X", Ui.color());
        g.text(font, "COLOR", rx + 14, cy, DIM, false);
        g.text(font, hex, rx + RW - 14 - font.width(hex), cy, TXT, false);
        for (int i = 0; i < 35; i++) g.fillGradient(sx + i * 4, sy, sx + i * 4 + 4, sy + sh, 0xFF000000 | Color.HSBtoRGB(Ui.d.h, i / 34f, 1f), 0xFF000000);
        int mx2 = sx + Math.round(Ui.d.s * (sw - 1)), my2 = sy + Math.round((1 - Ui.d.v) * (sh - 1));
        rl(g, mx2 - 3, my2 - 3, 7, 7, 3, 0xFFFFFFFF); rl(g, mx2 - 4, my2 - 4, 9, 9, 4, 0x88000000);
        int hy = sy + sh + 6;
        for (int i = 0; i < 35; i++) g.fill(sx + i * 4, hy, sx + i * 4 + 4, hy + 8, 0xFF000000 | Color.HSBtoRGB(i / 35f, 1f, 1f));
        int hx = sx + Math.round(Ui.d.h * (sw - 1)); g.fill(hx - 1, hy - 2, hx + 2, hy + 10, 0xFFFFFFFF);
        int wy = hy + 16;
        for (int i = 0; i < Ui.SWATCHES.length; i++) {
            int swx = rx + 10 + i * 18; rr(g, swx, wy, 14, 14, 3, 0xFF000000 | Ui.SWATCHES[i]);
            if (Ui.color() == Ui.SWATCHES[i]) rl(g, swx - 1, wy - 1, 16, 16, 4, 0xFFFFFFFF);
        }
        int y3 = tSteps();
        stepper(g, "Opacity", Math.round(Ui.d.alpha * 100 / 255f) + "%", rx, y3);
        stepper(g, "Corners", String.valueOf(Ui.d.radius), rx, y3 + 20);
        stepper(g, "Size", Math.round(Ui.d.scale * 150) + "%", rx, y3 + 40);
        g.text(font, "Blur", rx + 14, y3 + 64, DIM, false); button(g, Ui.d.blur ? "ON" : "OFF", rx + RW - 58, y3 + 60, 44, 16, Ui.d.blur);
    }

    void stepper(GuiGraphicsExtractor g, String label, String value, int rx, int y) {
        g.text(font, label, rx + 14, y + 4, DIM, false);
        button(g, "-", rx + RW - 64, y, 14, 14, false); g.centeredText(font, value, rx + RW - 38, y + 3, TXT); button(g, "+", rx + RW - 24, y, 14, 14, false);
    }

    void pick(double mx, double my) {
        int rx = x0 + PW - RW, sx = rx + 10, sy = tColorY() + 12;
        if (drag == 1) { Ui.d.s = (float) Math.max(0, Math.min(1, (mx - sx) / 139)); Ui.d.v = (float) Math.max(0, Math.min(1, 1 - (my - sy) / 55)); }
        else if (drag == 2) Ui.d.h = (float) Math.max(0, Math.min(0.999, (mx - sx) / 139));
        Ui.save();
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

    public void onFilesDrop(List<Path> paths) {
        int parts = 0; for (Path p : paths) if (p.toString().toLowerCase().endsWith(".zip")) parts += Math.max(0, Library.importZip(p));
        say(parts > 0 ? "Imported " + parts + " components" : "Drop a resource pack .zip");
    }

    // ---- input ----
    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) { layout(); return click(click.x() / sc, click.y() / sc, click.button()) || super.mouseClicked(click, doubled); }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (drag == 0) return false;
        layout(); pick(click.x() / sc, click.y() / sc); return true;
    }

    boolean click(double mx, double my, int b) {
        drag = 0; Col c = cur(); int rx = x0 + PW - RW;
        if (in(mx, my, x0 + 12, bot + 4, 56, 16)) { importAll(); return true; }
        if (in(mx, my, x0 + 72, bot + 4, 56, 16)) { presets = !presets; return true; }
        if (in(mx, my, x0 + 132, bot + 4, 46, 16)) { Library.sel.clear(); Library.save(); apply(); return true; }
        if (presets) for (int i = 0; i < 5; i++) {
            int y = bot - 86 + i * 16;
            if (in(mx, my, x0 + 72, y, 62, 14) && Library.presets.containsKey("" + i)) { Library.sel = new HashMap<>(Library.presets.get("" + i)); Library.save(); apply(); return true; }
            if (in(mx, my, x0 + 136, y, 34, 14)) { Library.presets.put("" + i, new HashMap<>(Library.sel)); Library.save(); say("Saved preset " + (i + 1)); return true; }
        }
        if (in(mx, my, rx, top, RW, 22)) { themes = mx >= rx + 80; return true; }
        if (themes && in(mx, my, rx, top + 22, RW, bot - top - 22)) {
            int y = tY(), sx = rx + 10, sy = tColorY() + 12, hy = sy + 62, wy = hy + 16, y3 = tSteps();
            for (int i = 0; i < Ui.STYLES.length; i++) if (in(mx, my, rx + 10, y + 12 + i * 17, RW - 24, 15)) { Ui.d.style = i; Ui.save(); return true; }
            if (in(mx, my, sx, sy, 140, 56)) { drag = 1; pick(mx, my); return true; }
            if (in(mx, my, sx, hy - 2, 140, 12)) { drag = 2; pick(mx, my); return true; }
            for (int i = 0; i < Ui.SWATCHES.length; i++) if (in(mx, my, rx + 10 + i * 18, wy, 14, 14)) { Ui.setRgb(Ui.SWATCHES[i]); Ui.save(); return true; }
            for (int k = 0; k < 3; k++) for (int sgn = -1; sgn <= 1; sgn += 2) if (in(mx, my, rx + RW + (sgn < 0 ? -64 : -24), y3 + k * 20, 14, 14)) {
                if (k == 0) Ui.d.alpha = Math.max(0x20, Math.min(0xF0, Ui.d.alpha + sgn * 0x11));
                if (k == 1) Ui.d.radius = Math.max(0, Math.min(12, Ui.d.radius + sgn));
                if (k == 2) Ui.d.scale = Math.max(0.4f, Math.min(1f, Math.round((Ui.d.scale + sgn * 0.05f) * 100) / 100f));
                Ui.save(); return true;
            }
            if (in(mx, my, rx + RW - 58, y3 + 60, 44, 16)) { Ui.d.blur = !Ui.d.blur; Ui.save(); return true; }
            return true;
        }
        if (in(mx, my, x0, top, LW, 24)) { int n = groups().size(); gi = (gi + (mx < x0 + LW / 2 ? -1 : 1) + n) % n; resetView(); return true; }
        var v = visible();
        for (int k = 0; k < visCols() && colRow + k < v.size(); k++) if (in(mx, my, x0 + 6, top + 26 + k * 26, LW - 12, 26)) { colIdx = colRow + k; gridRow = 0; return true; }
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

    @Override public boolean keyPressed(KeyEvent in) { return key(in.key()) || super.keyPressed(in); }

    boolean key(int k) {
        if (searching) {
            if (k == GLFW_KEY_BACKSPACE && !query.isEmpty()) query = query.substring(0, query.length() - 1);
            else if (k == GLFW_KEY_ENTER || k == GLFW_KEY_ESCAPE) searching = false;
            return true;
        }
        Col c = cur();
        if (k == GLFW_KEY_SLASH) { searching = skipChar = true; return true; }
        if (k == GLFW_KEY_F && hasHover && hover != null && c != null) { fav(c, hover); return true; }
        if (k == GLFW_KEY_K) { onClose(); return true; }
        return false;
    }

    @Override public boolean charTyped(CharacterEvent in) {
        if (!searching) return false;
        if (skipChar) { skipChar = false; return true; }
        int cp = in.codepoint();
        if (cp >= 32 && query.length() < 24) { query += Character.toString(cp); gridRow = 0; }
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
