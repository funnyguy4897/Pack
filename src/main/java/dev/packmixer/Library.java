package dev.packmixer;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Library on disk: packmixer/library/<collection>/<style>/{style.json, assets/...}. A selection is composed into resourcepacks/PackMixer. */
public final class Library {
    public static class StyleDef { String name; float hue, sat = 1, bri = 1; String mul = "FFFFFF"; }
    public static class Col { public String id, name, group = "main", preview, alt, cube, side; List<String> globs = List.of(), tint = List.of(); List<StyleDef> styles = List.of(); }
    public static class Style { public String col, id, name, author; public boolean builtin; transient Path dir; }
    static class Root { List<Col> collections; }

    public static final Gson G = new GsonBuilder().setPrettyPrinting().create();
    public static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("packmixer");
    public static final Path LIB = ROOT.resolve("library"), IMPORT = ROOT.resolve("import");
    static final String PACK_ID = "file/PackMixer";
    public static volatile long dirtyAt;
    public static List<Col> cols = List.of();
    public static final Map<String, List<Style>> styles = new HashMap<>();
    public static Map<String, String> sel = new HashMap<>();
    public static Set<String> favs = new HashSet<>();
    public static Map<String, Map<String, String>> presets = new HashMap<>();
    static final Map<Path, Optional<Path>> PREV = new HashMap<>();

    static String slug(String s) { return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""); }
    static <T> T read(String f, Type t, T def) {
        try { Path p = ROOT.resolve(f); if (Files.exists(p)) { T v = G.fromJson(Files.readString(p), t); if (v != null) return v; } } catch (Exception ignored) {}
        return def;
    }
    static void write(Path p, Object o) { try { Files.createDirectories(p.getParent()); Files.writeString(p, G.toJson(o)); } catch (IOException e) { e.printStackTrace(); } }
    public static void save() { write(ROOT.resolve("selection.json"), sel); write(ROOT.resolve("favorites.json"), favs); write(ROOT.resolve("presets.json"), presets); }

    public static void init(Minecraft mc) {
        try (var r = new InputStreamReader(Library.class.getResourceAsStream("/packmixer/collections.json"))) { cols = G.fromJson(r, Root.class).collections; }
        catch (Exception e) { throw new RuntimeException(e); }
        purgeIfOutdated();
        Ui.load();
        sel = read("selection.json", new TypeToken<Map<String, String>>() {}.getType(), new HashMap<>());
        favs = read("favorites.json", new TypeToken<Set<String>>() {}.getType(), new HashSet<>());
        presets = read("presets.json", new TypeToken<Map<String, Map<String, String>>>() {}.getType(), new HashMap<>());
        try { Files.createDirectories(IMPORT); } catch (IOException ignored) {}
        for (Col c : cols) for (StyleDef d : c.styles) {
            Path dir = LIB.resolve(c.id).resolve(slug(d.name));
            if (!Files.exists(dir.resolve("style.json"))) makeBuiltin(mc, c, d, dir);
        }
        scan();
    }

    /** Built-in styles are original recolours generated from the vanilla textures on this machine. */
    static void makeBuiltin(Minecraft mc, Col c, StyleDef d, Path dir) {
        try {
            int n = 0; ClassLoader cl = Library.class.getClassLoader();
            for (String f : c.tint) {
                BufferedImage src; try (var is = cl.getResourceAsStream("assets/minecraft/" + f)) { if (is == null) continue; src = ImageIO.read(is); }
                BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
                img.getGraphics().drawImage(src, 0, 0, null);
                tint(img, d);
                Path out = dir.resolve("assets/minecraft/" + f);
                Files.createDirectories(out.getParent());
                ImageIO.write(img, "png", out.toFile()); n++;
                try (var is = cl.getResourceAsStream("assets/minecraft/" + f + ".mcmeta")) { if (is != null) Files.copy(is, Path.of(out + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING); }
            }
            if (n > 0) { Style s = new Style(); s.name = d.name; s.author = "Pack Mixer"; s.builtin = true; write(dir.resolve("style.json"), s); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    static void tint(BufferedImage im, StyleDef d) {
        int m = Integer.parseInt(d.mul, 16); float mr = (m >> 16 & 255) / 255f, mg = (m >> 8 & 255) / 255f, mb = (m & 255) / 255f;
        float[] hsb = new float[3];
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
            int p = im.getRGB(x, y), a = p >>> 24; if (a == 0) continue;
            Color.RGBtoHSB(p >> 16 & 255, p >> 8 & 255, p & 255, hsb);
            int q = Color.HSBtoRGB((hsb[0] + d.hue) % 1f, Math.min(1, hsb[1] * d.sat), Math.min(1, hsb[2] * d.bri));
            im.setRGB(x, y, a << 24 | (int) ((q >> 16 & 255) * mr) << 16 | (int) ((q >> 8 & 255) * mg) << 8 | (int) ((q & 255) * mb));
        }
    }

    public static void scan() {
        styles.clear(); PREV.clear();
        for (Col c : cols) {
            List<Style> l = new ArrayList<>(); Path cd = LIB.resolve(c.id);
            if (Files.isDirectory(cd)) try (var s = Files.list(cd)) {
                for (Path d : (Iterable<Path>) s::iterator) {
                    Path j = d.resolve("style.json"); if (!Files.exists(j)) continue;
                    Style st = G.fromJson(Files.readString(j), Style.class); st.col = c.id; st.id = d.getFileName().toString(); st.dir = d; l.add(st);
                }
            } catch (IOException ignored) {}
            l.sort(Comparator.comparing(s -> s.name.toLowerCase()));
            styles.put(c.id, l);
        }
    }

    public static Path previewPath(Col c, Style s) {
        return PREV.computeIfAbsent(s.dir, k -> {
            if (c.cube != null) { Path q = cube(c, s); if (q != null) return Optional.of(q); }
            for (String rel : new String[]{c.preview, c.alt}) {
                if (rel == null) continue;
                Path p = k.resolve("assets/minecraft/" + rel);
                if (Files.exists(p)) return Optional.of(p);
            }
            try (var w = Files.walk(k.resolve("assets"))) { return w.filter(x -> x.toString().endsWith(".png")).findFirst(); }
            catch (IOException e) { return Optional.empty(); }
        }).orElse(null);
    }

    static BufferedImage sq(BufferedImage i) { int w = i.getWidth(); return i.getHeight() > w ? i.getSubimage(0, 0, w, w) : i; }

    /** Renders an isometric block preview (top + two sides) of a style's texture into preview-cube.png. */
    static Path cube(Col c, Style s) {
        Path out = s.dir.resolve("preview-cube.png");
        if (Files.exists(out)) return out;
        try {
            if (c.preview == null) return null;
            Path base = s.dir.resolve("assets/minecraft"), p = base.resolve(c.preview);
            if (!Files.exists(p)) return null;
            BufferedImage top, left, right, t = ImageIO.read(p.toFile());
            if ("crystal".equals(c.cube)) {   // glass cube inside the end crystal sheet (64x32 layout, any scale)
                int e = Math.round(8 * t.getWidth() / 64f);
                top = t.getSubimage(e, 0, e, e); left = t.getSubimage(e, e, e, e); right = t.getSubimage(2 * e, e, e, e);
            } else {
                top = sq(t); Path sp = c.side == null ? p : base.resolve(c.side); if (!Files.exists(sp)) sp = p;
                left = right = sq(ImageIO.read(sp.toFile()));
            }
            BufferedImage o = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                double a = (x + .5 - 32) / 32, b = (y + .5 - 16) / 16, u, v, sh; BufferedImage src;
                if (Math.abs(a) + Math.abs(b) <= 1) { src = top; u = (a + b + 1) / 2; v = (b - a + 1) / 2; sh = 1; }
                else if (x < 32) { double yt = 16 + (x + .5) / 2; if (y + .5 < yt || y + .5 >= yt + 32) continue; src = left; u = (x + .5) / 32; v = (y + .5 - yt) / 32; sh = .8; }
                else { double yt = 32 - (x + .5 - 32) / 2; if (y + .5 < yt || y + .5 >= yt + 32) continue; src = right; u = (x + .5 - 32) / 32; v = (y + .5 - yt) / 32; sh = .58; }
                int px = src.getRGB(Math.min(src.getWidth() - 1, (int) (u * src.getWidth())), Math.min(src.getHeight() - 1, (int) (v * src.getHeight())));
                int al = px >>> 24; if (al == 0) continue;
                o.setRGB(x, y, al << 24 | (int) ((px >> 16 & 255) * sh) << 16 | (int) ((px >> 8 & 255) * sh) << 8 | (int) ((px & 255) * sh));
            }
            ImageIO.write(o, "png", out.toFile()); return out;
        } catch (Exception e) { return null; }
    }

    /** 1.21.11 moved the sun/moon into textures/environment/celestial/. Convert older sky packs so they still work. */
    static void legacySky(Path mc) {
        try {
            Path env = mc.resolve("textures/environment"), cel = env.resolve("celestial"), sun = env.resolve("sun.png"), moon = env.resolve("moon_phases.png");
            if (Files.exists(sun) && !Files.exists(cel.resolve("sun.png"))) { Files.createDirectories(cel); Files.copy(sun, cel.resolve("sun.png")); }
            if (Files.exists(moon) && !Files.exists(cel.resolve("moon/full_moon.png"))) {
                BufferedImage m = ImageIO.read(moon.toFile()); int w = m.getWidth() / 4, h = m.getHeight() / 2;
                String[] n = {"full_moon", "waning_gibbous", "third_quarter", "waning_crescent", "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"};
                Files.createDirectories(cel.resolve("moon"));
                for (int i = 0; i < 8; i++) ImageIO.write(m.getSubimage(i % 4 * w, i / 4 * h, w, h), "png", cel.resolve("moon/" + n[i] + ".png").toFile());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    static final String VER = "4";
    /** Built-in styles are regenerated when the mod's catalogue changes. Imported styles are never touched. */
    static void purgeIfOutdated() {
        Path f = ROOT.resolve("version.txt");
        try {
            if (Files.exists(f) && Files.readString(f).trim().equals(VER)) return;
            if (Files.isDirectory(LIB)) for (Path cd : Files.list(LIB).toList()) if (Files.isDirectory(cd)) for (Path d : Files.list(cd).toList()) {
                Path j = d.resolve("style.json");
                if (Files.exists(j) && G.fromJson(Files.readString(j), Style.class).builtin) deleteTree(d);
            }
            Files.createDirectories(ROOT); Files.writeString(f, VER);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Splits any resource pack zip into per-collection styles. Returns number of components extracted, or -1 on failure. */
    public static int importZip(Path zip) {
        String base = zip.getFileName().toString().replaceFirst("(?i)\\.zip$", ""), id = slug(base); int n = 0;
        try (ZipFile z = new ZipFile(zip.toFile())) {
            List<? extends ZipEntry> all = Collections.list(z.entries()); String pre = "";
            for (var e : all) { int i = e.getName().indexOf("assets/minecraft/"); if (i >= 0) { pre = e.getName().substring(0, i); break; } }
            for (Col c : cols) {
                var ms = c.globs.stream().map(g -> FileSystems.getDefault().getPathMatcher("glob:assets/minecraft/" + g)).toList();
                Path dir = LIB.resolve(c.id).resolve(id); int count = 0;
                for (var e : all) {
                    if (e.isDirectory() || !e.getName().startsWith(pre)) continue;
                    String rel = e.getName().substring(pre.length()), key = rel.endsWith(".mcmeta") ? rel.substring(0, rel.length() - 7) : rel;
                    try {
                        Path kp = Path.of(key); if (ms.stream().noneMatch(m -> m.matches(kp))) continue;
                        Path out = dir.resolve(rel).normalize(); if (!out.startsWith(dir)) continue;
                        if (count++ == 0) deleteTree(dir);
                        Files.createDirectories(out.getParent());
                        try (var in = z.getInputStream(e)) { Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING); }
                    } catch (InvalidPathException ignored) {}
                }
                if (count > 0) { Style s = new Style(); s.name = base; s.author = "Imported"; write(dir.resolve("style.json"), s); n++; }
            }
        } catch (Exception ex) { ex.printStackTrace(); return -1; }
        scan(); return n;
    }

    static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (var w = Files.walk(p)) { w.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.delete(x); } catch (IOException ignored) {} }); } catch (IOException ignored) {}
    }

    /** Builds resourcepacks/PackMixer from the current selection, enables it on top, and reloads. */
    public static void compose(Minecraft mc) {
        Path out = mc.getResourcePackDirectory().resolve("PackMixer");
        deleteTree(out);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Pack Mixer loadout\",\"min_format\":69,\"max_format\":999}}");
            for (var e : sel.entrySet()) {
                Style s = styles.getOrDefault(e.getKey(), List.of()).stream().filter(x -> x.id.equals(e.getValue())).findFirst().orElse(null);
                if (s == null) continue;
                Path src = s.dir.resolve("assets"); if (!Files.isDirectory(src)) continue;
                try (var w = Files.walk(src)) {
                    for (Path p : (Iterable<Path>) w::iterator) if (Files.isRegularFile(p)) {
                        Path t = out.resolve("assets").resolve(src.relativize(p).toString());
                        Files.createDirectories(t.getParent()); Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        legacySky(out.resolve("assets/minecraft"));
        var o = mc.options; o.resourcePacks.remove(PACK_ID); o.resourcePacks.add(PACK_ID); o.save();
        var m = mc.getResourcePackRepository(); m.reload(); m.setSelected(o.resourcePacks);
        mc.reloadResourcePacks();
    }
}
