package dev.packmixer;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

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
    public static class Col { public String id, name, group = "main", preview; List<String> globs = List.of(), tint = List.of(); List<StyleDef> styles = List.of(); }
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

    public static void init(MinecraftClient mc) {
        try (var r = new InputStreamReader(Library.class.getResourceAsStream("/packmixer/collections.json"))) { cols = G.fromJson(r, Root.class).collections; }
        catch (Exception e) { throw new RuntimeException(e); }
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
    static void makeBuiltin(MinecraftClient mc, Col c, StyleDef d, Path dir) {
        try {
            var pack = mc.getDefaultResourcePack();
            for (String f : c.tint) {
                var in = pack.open(ResourceType.CLIENT_RESOURCES, Identifier.of("minecraft", f));
                if (in == null) continue;
                BufferedImage src; try (var is = in.get()) { src = ImageIO.read(is); }
                BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
                img.getGraphics().drawImage(src, 0, 0, null);
                tint(img, d);
                Path out = dir.resolve("assets/minecraft/" + f);
                Files.createDirectories(out.getParent());
                ImageIO.write(img, "png", out.toFile());
                var meta = pack.open(ResourceType.CLIENT_RESOURCES, Identifier.of("minecraft", f + ".mcmeta"));
                if (meta != null) try (var is = meta.get()) { Files.copy(is, Path.of(out + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING); }
            }
            Style s = new Style(); s.name = d.name; s.author = "Pack Mixer"; s.builtin = true;
            write(dir.resolve("style.json"), s);
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
            Path p = k.resolve("assets/minecraft/" + c.preview);
            if (Files.exists(p)) return Optional.of(p);
            try (var w = Files.walk(k.resolve("assets"))) { return w.filter(x -> x.toString().endsWith(".png")).findFirst(); }
            catch (IOException e) { return Optional.empty(); }
        }).orElse(null);
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
    public static void compose(MinecraftClient mc) {
        Path out = mc.getResourcePackDir().resolve("PackMixer");
        deleteTree(out);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":75,\"description\":\"Pack Mixer loadout\"}}");
            for (var e : sel.entrySet()) {
                Style s = styles.getOrDefault(e.getKey(), List.of()).stream().filter(x -> x.id.equals(e.getValue())).findFirst().orElse(null);
                if (s == null) continue;
                Path src = s.dir.resolve("assets");
                try (var w = Files.walk(src)) {
                    for (Path p : (Iterable<Path>) w::iterator) if (Files.isRegularFile(p)) {
                        Path t = out.resolve("assets").resolve(src.relativize(p).toString());
                        Files.createDirectories(t.getParent()); Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        var o = mc.options; o.resourcePacks.remove(PACK_ID); o.resourcePacks.add(PACK_ID); o.write();
        var m = mc.getResourcePackManager(); m.scanPacks(); m.setEnabledProfiles(o.resourcePacks);
        mc.reloadResources();
    }
}
