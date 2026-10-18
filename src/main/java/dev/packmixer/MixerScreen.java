package dev.packmixer;

import dev.packmixer.Library.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
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
    static final int BG = 0xFF0B0912, PANEL = 0xFF14101D, CARD = 0xFF1B1628, LINE = 0xFF2A2340, ACC = 0xFFA78BFA, TXT = 0xFFE9E4F5, DIM = 0xFF8B829F, GRN = 0xFF4ADE80, GOLD = 0xFFFBBF24;
    record Tex(Identifier id, int w, int h) {}
    static final Map<Path, Tex> TEX = new HashMap<>();

    int x0, y0, pw, ph, top, bot, cx, cw; static final int LW = 132, RW = 150, HH = 28, FH = 24;
    boolean extras, searching, favOnly, presets, skipChar, hasHover;
    String query = "", toast = ""; long toastAt; int colIdx, colScroll, gridScroll; Style hover;

    final Screen parent;
    public MixerScreen() { this(null); }
    public MixerScreen(Screen parent) { super(Text.literal("Pack Mixer")); this.parent = parent; if (Library.cols.isEmpty()) Library.init(net.minecraft.client.MinecraftClient.getInstance()); }

    void layout() { pw = Math.min(width - 16, 900); ph = Math.min(height - 16, 520); x0 = (width - pw) / 2; y0 = (height - ph) / 2; top = y0 + HH; bot = y0 + ph - FH; cx = x0 + LW; cw = pw - LW - RW; }
    List<Col> visible() { return Library.cols.stream().filter(c -> extras == c.group.equals("extras")).toList(); }
    Col cur() { var v = visible(); return v.isEmpty() ? null : v.get(Math.min(colIdx, v.size() - 1)); }
    Style selected(Col c) { String id = Library.sel.get(c.id); return Library.styles.get(c.id).stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
    List<Style> items(Col c) {
        var l = new ArrayList<Style>(); if (query.isEmpty() && !favOnly) l.add(null);
        for (Style s : Library.styles.get(c.id)) if ((!favOnly || Library.favs.contains(c.id + "/" + s.id)) && s.name.toLowerCase().contains(query.toLowerCase())) l.add(s);
        return l;
    }
    int perRow() { return Math.max(1, (cw - 8) / 102); }
    int sbx() { return cx + cw - 124; }
    int favX() { return sbx() - 52; }
    int allX() { return favX() - 24; }
    boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    void apply() { Library.dirtyAt = System.currentTimeMillis(); }
    void say(String s) { toast = s; toastAt = System.currentTimeMillis(); }

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
        g.fill(x, y, x + w, y + h, 0xFF100C1A);
        if (s == null) { g.drawCenteredTextWithShadow(textRenderer, "Vanilla", x + w / 2, y + h / 2 - 4, DIM); return; }
        Path p = Library.previewPath(c, s); if (p == null) return;
        Tex t = tex(p); if (t.id() == null) return;
        float sc = Math.min((w - 4f) / t.w(), (h - 4f) / t.h()); int dw = Math.max(1, (int) (t.w() * sc)), dh = Math.max(1, (int) (t.h() * sc));
        g.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh, t.w(), t.h(), t.w(), t.h());
    }

    void button(DrawContext g, String label, int x, int y, int w, int h, boolean on) {
        g.fill(x, y, x + w, y + h, CARD); g.drawStrokedRectangle(x, y, w, h, on ? ACC : LINE);
        g.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, on ? TXT : DIM);
    }

    @Override public void render(DrawContext g, int mx, int my, float d) {
        layout(); long now = System.currentTimeMillis();
        g.fill(0, 0, width, height, 0xC0050309);
        g.fill(x0, y0, x0 + pw, y0 + ph, BG); g.drawStrokedRectangle(x0, y0, pw, ph, LINE);

        // header + loadout
        g.drawText(textRenderer, Text.literal("PACK MIXER").formatted(Formatting.BOLD), x0 + 14, y0 + 10, ACC, false);
        g.drawText(textRenderer, "STYLE ARCHIVE", x0 + 92, y0 + 10, DIM, false);
        var main = Library.cols.stream().filter(c -> c.group.equals("main")).limit(9).toList();
        for (int i = 0; i < main.size(); i++) {
            int sx = x0 + pw - 10 - (main.size() - i) * 20; Col c = main.get(i); Style s = selected(c);
            prev(g, c, s == null ? null : s, sx, y0 + 5, 18, 18);
            if (s == null) g.fill(sx, y0 + 5, sx + 18, y0 + 23, 0xFF100C1A);
            g.drawStrokedRectangle(sx, y0 + 5, 18, 18, s == null ? LINE : ACC);
        }
        g.drawText(textRenderer, "LOADOUT", x0 + pw - 20 - main.size() * 20 - 44, y0 + 10, DIM, false);
        g.fill(x0, top, x0 + pw, top + 1, LINE);

        // left: collections
        g.fill(x0 + 1, top + 1, cx, bot, PANEL);
        g.drawText(textRenderer, "COLLECTIONS", x0 + 10, top + 6, extras ? DIM : ACC, false);
        g.drawText(textRenderer, "EXTRAS", x0 + LW / 2 + 22, top + 6, extras ? ACC : DIM, false);
        var v = visible(); colScroll = Math.max(0, Math.min(colScroll, v.size() * 26 + 22 - (bot - top)));
        g.enableScissor(x0 + 1, top + 20, cx, bot);
        for (int i = 0; i < v.size(); i++) {
            Col c = v.get(i); int y = top + 22 + i * 26 - colScroll; boolean act = c == cur(); Style s = selected(c);
            if (act) { g.fill(x0 + 4, y, cx - 4, y + 24, CARD); g.fill(x0 + 4, y, x0 + 6, y + 24, ACC); }
            prev(g, c, s, x0 + 10, y + 4, 16, 16);
            g.drawText(textRenderer, c.name, x0 + 32, y + 4, act ? TXT : 0xFFC9C2DB, false);
            g.drawText(textRenderer, textRenderer.trimToWidth(s == null ? "Vanilla" : s.name, 64), x0 + 32, y + 14, DIM, false);
            String n = String.valueOf(Library.styles.get(c.id).size()); g.drawText(textRenderer, n, cx - 10 - textRenderer.getWidth(n), y + 8, act ? ACC : DIM, false);
        }
        g.disableScissor(); g.fill(cx, top + 1, cx + 1, bot, LINE);

        // centre: style grid
        Col c = cur(); hover = null; hasHover = false; Style sel = c == null ? null : selected(c);
        if (c != null) {
            var it = items(c);
            g.drawText(textRenderer, Text.literal(c.name).formatted(Formatting.BOLD), cx + 10, top + 8, TXT, false);
            g.drawText(textRenderer, Library.styles.get(c.id).size() + " styles · " + (sel == null ? "Vanilla" : sel.name), cx + 10, top + 20, DIM, false);
            g.fill(sbx(), top + 6, sbx() + 114, top + 24, CARD); g.drawStrokedRectangle(sbx(), top + 6, 114, 18, searching ? ACC : LINE);
            g.drawText(textRenderer, query.isEmpty() && !searching ? "/ Search" : query + (searching ? "_" : ""), sbx() + 5, top + 11, query.isEmpty() ? DIM : TXT, false);
            long fc = Library.favs.stream().filter(s -> s.startsWith(c.id + "/")).count();
            g.drawText(textRenderer, "All", allX(), top + 11, favOnly ? DIM : ACC, false);
            g.drawText(textRenderer, "★ " + fc, favX(), top + 11, favOnly ? ACC : DIM, false);
            int n = perRow(), rows = (it.size() + n - 1) / n; gridScroll = Math.max(0, Math.min(gridScroll, rows * 76 + 46 - (bot - top)));
            g.enableScissor(cx + 1, top + 36, cx + cw, bot);
            for (int i = 0; i < it.size(); i++) {
                Style s = it.get(i); int x = cx + 8 + (i % n) * 102, y = top + 42 + (i / n) * 76 - gridScroll;
                if (y > bot || y + 70 < top) continue;
                boolean hv = in(mx, my, x, y, 96, 70) && my >= top + 36 && my < bot, on = s == sel;
                if (hv) { hover = s; hasHover = true; }
                prev(g, c, s, x, y, 96, 54);
                g.drawStrokedRectangle(x, y, 96, 54, on ? ACC : hv ? 0xFF5B4B8A : LINE);
                if (on) g.fill(x + 3, y + 3, x + 7, y + 7, GRN);
                if (s != null && Library.favs.contains(c.id + "/" + s.id)) g.drawText(textRenderer, "★", x + 86, y + 3, GOLD, false);
                g.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(s == null ? "Vanilla" : s.name, 92), x + 48, y + 58, on ? TXT : 0xFFC9C2DB);
            }
            g.disableScissor();
        }

        // right: inspector
        int rx = x0 + pw - RW; g.fill(rx, top + 1, x0 + pw - 1, bot, PANEL); g.fill(rx, top + 1, rx + 1, bot, LINE);
        g.drawText(textRenderer, "INSPECTOR", rx + 10, top + 6, ACC, false);
        if (c != null) {
            Style f = hasHover ? hover : sel;
            prev(g, c, f, rx + 8, top + 22, RW - 16, 90); g.drawStrokedRectangle(rx + 8, top + 22, RW - 16, 90, LINE);
            g.drawText(textRenderer, Text.literal(f == null ? "Vanilla" : f.name).formatted(Formatting.BOLD), rx + 8, top + 120, TXT, false);
            if (f != null) g.drawText(textRenderer, "By " + f.author + (f.builtin ? " · built in" : ""), rx + 8, top + 134, DIM, false);
            g.drawText(textRenderer, "Now: " + (sel == null ? "Vanilla" : sel.name), rx + 8, top + 148, DIM, false);
            button(g, f == sel ? "ACTIVE" : "CLICK CARD TO APPLY", rx + 8, bot - 28, RW - 16, 20, f == sel);
        }

        // footer
        g.fill(x0 + 1, bot, x0 + pw - 1, y0 + ph - 1, PANEL); g.fill(x0, bot, x0 + pw, bot + 1, LINE);
        button(g, "UPLOAD", x0 + 10, bot + 4, 56, 16, false); button(g, "PRESETS", x0 + 70, bot + 4, 56, 16, presets); button(g, "RESET", x0 + 130, bot + 4, 46, 16, false);
        g.drawCenteredTextWithShadow(textRenderer, "CLICK APPLY · F FAVORITE · / SEARCH · DROP .ZIP TO IMPORT", x0 + pw / 2 + 40, bot + 8, DIM);
        g.drawText(textRenderer, "OPEN: K", x0 + pw - 52, bot + 8, DIM, false);
        if (presets) for (int i = 0; i < 5; i++) {
            int y = bot - 84 + i * 16; boolean has = Library.presets.containsKey("" + i);
            button(g, "Preset " + (i + 1) + (has ? " ●" : ""), x0 + 70, y, 62, 16, has); button(g, "Save", x0 + 132, y, 34, 16, false);
        }
        if (now - toastAt < 3500) g.drawCenteredTextWithShadow(textRenderer, toast, x0 + pw / 2, y0 + 34, GRN);
    }

    void importAll() {
        int files = 0, parts = 0;
        try (var s = Files.list(Library.IMPORT)) {
            for (Path p : (Iterable<Path>) s::iterator) if (p.toString().toLowerCase().endsWith(".zip")) {
                int r = Library.importZip(p); if (r >= 0) { files++; parts += r; Files.createDirectories(Library.IMPORT.resolve("done")); Files.move(p, Library.IMPORT.resolve("done").resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING); }
            }
        } catch (Exception ignored) {}
        if (files == 0) say("Drop a pack .zip here, or put zips in .minecraft/packmixer/import");
        else say("Imported " + files + " pack(s) → " + parts + " components");
    }

    public void onFilesDropped(List<Path> paths) {
        int parts = 0; for (Path p : paths) if (p.toString().toLowerCase().endsWith(".zip")) parts += Math.max(0, Library.importZip(p));
        say(parts > 0 ? "Imported " + parts + " components" : "Drop a resource pack .zip");
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) { return click(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled); }

    boolean click(double mx, double my, int b) {
        layout(); Col c = cur();
        if (in(mx, my, x0 + 10, bot + 4, 56, 16)) { importAll(); return true; }
        if (in(mx, my, x0 + 70, bot + 4, 56, 16)) { presets = !presets; return true; }
        if (in(mx, my, x0 + 130, bot + 4, 46, 16)) { Library.sel.clear(); Library.save(); apply(); return true; }
        if (presets) for (int i = 0; i < 5; i++) {
            int y = bot - 84 + i * 16;
            if (in(mx, my, x0 + 70, y, 62, 16) && Library.presets.containsKey("" + i)) { Library.sel = new HashMap<>(Library.presets.get("" + i)); Library.save(); apply(); return true; }
            if (in(mx, my, x0 + 132, y, 34, 16)) { Library.presets.put("" + i, new HashMap<>(Library.sel)); Library.save(); say("Saved preset " + (i + 1)); return true; }
        }
        if (in(mx, my, x0, top, LW, 20)) { extras = mx >= x0 + LW / 2; colIdx = 0; colScroll = gridScroll = 0; return true; }
        var v = visible();
        for (int i = 0; i < v.size(); i++) if (in(mx, my, x0, top + 22 + i * 26 - colScroll, LW, 26) && my >= top + 20 && my < bot) { colIdx = i; gridScroll = 0; return true; }
        if (c == null) return false;
        searching = in(mx, my, sbx(), top + 6, 114, 18);
        if (in(mx, my, favX(), top + 6, 40, 18)) favOnly = true; if (in(mx, my, allX(), top + 6, 20, 18)) favOnly = false;
        if (in(mx, my, cx, top + 36, cw, bot - top - 36)) {
            var it = items(c); int n = perRow();
            for (int i = 0; i < it.size(); i++) if (in(mx, my, cx + 8 + (i % n) * 102, top + 42 + (i / n) * 76 - gridScroll, 96, 70)) {
                Style s = it.get(i);
                if (b == 0) { if (s == null) Library.sel.remove(c.id); else Library.sel.put(c.id, s.id); Library.save(); apply(); }
                else if (s != null) fav(c, s);
                return true;
            }
        }
        return false;
    }

    void fav(Col c, Style s) { String k = c.id + "/" + s.id; if (!Library.favs.remove(k)) Library.favs.add(k); Library.save(); }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < cx) colScroll = Math.max(0, colScroll - (int) (v * 20)); else gridScroll = Math.max(0, gridScroll - (int) (v * 24));
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
        if (cp >= 32 && query.length() < 24) { query += Character.toString(cp); gridScroll = 0; }
        return true;
    }

    @Override public void close() { client.setScreen(parent); }
}
