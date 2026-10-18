# Generates mc-26.1.2/ and mc-26.2/ (Mojang-named sources) from the 1.21.11 (Yarn) sources in src/. Run from the repo root.
import os, re, shutil, json
SRC = 'src/main/java/dev/packmixer'; RES = 'src/main/resources'

def sub(s, a, b, all_=False):
    assert a in s, a[:70]
    return s.replace(a, b) if all_ else s.replace(a, b, 1)

def port_screen(s, v262):
    for a, b in [
        ('import net.minecraft.client.gl.RenderPipelines;', 'import net.minecraft.client.renderer.RenderPipelines;'),
        ('import net.minecraft.client.gui.Click;', 'import net.minecraft.client.input.MouseButtonEvent;'),
        ('import net.minecraft.client.gui.DrawContext;', 'import net.minecraft.client.gui.GuiGraphicsExtractor;'),
        ('import net.minecraft.client.gui.screen.Screen;', 'import net.minecraft.client.gui.screens.Screen;'),
        ('import net.minecraft.client.input.CharInput;', 'import net.minecraft.client.input.CharacterEvent;'),
        ('import net.minecraft.client.input.KeyInput;', 'import net.minecraft.client.input.KeyEvent;'),
        ('import net.minecraft.client.texture.NativeImage;', 'import com.mojang.blaze3d.platform.NativeImage;'),
        ('import net.minecraft.client.texture.NativeImageBackedTexture;', 'import net.minecraft.client.renderer.texture.DynamicTexture;'),
        ('import net.minecraft.text.Text;', 'import net.minecraft.network.chat.Component;'),
        ('import net.minecraft.util.Formatting;', 'import net.minecraft.ChatFormatting;'),
        ('import net.minecraft.util.Identifier;', 'import net.minecraft.resources.Identifier;'),
    ]: s = sub(s, a, b)
    s = sub(s, 'if (Ui.d.blur || client.world == null) renderBackground(g, rmx, rmy, d); else',
               'if (Ui.d.blur || minecraft.level == null) super.extractRenderState(g, rmx, rmy, d); else')
    s = sub(s, '@Override public void render(DrawContext g, int rmx, int rmy, float d)',
               '@Override public void extractRenderState(DrawContext g, int rmx, int rmy, float d)')
    for a, b in [
        ('DrawContext', 'GuiGraphicsExtractor'), ('Text.literal(', 'Component.literal('),
        ('.formatted(Formatting.BOLD)', '.withStyle(ChatFormatting.BOLD)'),
        ('g.drawText(textRenderer, ', 'g.text(font, '), ('g.drawCenteredTextWithShadow(textRenderer, ', 'g.centeredText(font, '),
        ('textRenderer.trimToWidth(', 'font.plainSubstrByWidth('), ('textRenderer.getWidth(', 'font.width('),
        ('g.drawTexture(', 'g.blit('), ('g.getMatrices()', 'g.pose()'),
        ('client.getTextureManager().registerTexture(', 'minecraft.getTextureManager().register('),
        ('new NativeImageBackedTexture(', 'new DynamicTexture('), ('Identifier.of(', 'Identifier.fromNamespaceAndPath('),
        ('Click click', 'MouseButtonEvent click'), ('KeyInput in', 'KeyEvent in'), ('CharInput in', 'CharacterEvent in'),
        ('public void onFilesDropped(', 'public void onFilesDrop('),
        ('net.minecraft.client.MinecraftClient.getInstance()', 'net.minecraft.client.Minecraft.getInstance()'),
        ('{ close(); return true; }', '{ onClose(); return true; }'),
    ]: s = sub(s, a, b, True)
    s = sub(s, '@Override public void close() { client.setScreen(parent); }',
            '@Override public void onClose() { minecraft.gui.setScreen(parent); }' if v262 else '@Override public void onClose() { minecraft.setScreen(parent); }')
    assert 'textRenderer' not in s and 'DrawContext' not in s and not re.search(r'(?<![\w.])client\.', s)
    return s

def port_library(s, pf):
    s = sub(s, 'import net.minecraft.client.MinecraftClient;\nimport net.minecraft.resource.ResourceType;\nimport net.minecraft.util.Identifier;\n', 'import net.minecraft.client.Minecraft;\n')
    s = sub(s, 'MinecraftClient mc', 'Minecraft mc', True)
    s = sub(s, 'var pack = mc.getDefaultResourcePack(); int n = 0;', 'int n = 0; ClassLoader cl = Library.class.getClassLoader();')   # vanilla textures come from the game jar
    s, k1 = re.subn(r'                var in = pack\.open\(.*?\n                if \(in == null\) continue;\n                BufferedImage src; try \(var is = in\.get\(\)\) \{ src = ImageIO\.read\(is\); \}',
        '                BufferedImage src; try (var is = cl.getResourceAsStream("assets/minecraft/" + f)) { if (is == null) continue; src = ImageIO.read(is); }', s, count=1, flags=re.S)
    s, k2 = re.subn(r'                var meta = pack\.open\(.*?\n                if \(meta != null\) try \(var is = meta\.get\(\)\) \{ Files\.copy\(is, Path\.of\(out \+ "\.mcmeta"\), StandardCopyOption\.REPLACE_EXISTING\); \}',
        '                try (var is = cl.getResourceAsStream("assets/minecraft/" + f + ".mcmeta")) { if (is != null) Files.copy(is, Path.of(out + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING); }', s, count=1, flags=re.S)
    assert k1 == 1 and k2 == 1 and 'pack.open' not in s and 'Identifier' not in s
    s = sub(s, 'mc.getResourcePackDir()', 'mc.getResourcePackDirectory()')
    s = sub(s, 'o.write();', 'o.save();')
    s = sub(s, 'var m = mc.getResourcePackManager(); m.scanPacks(); m.setEnabledProfiles(o.resourcePacks);\n        mc.reloadResources();',
               'var m = mc.getResourcePackRepository(); m.reload(); m.setSelected(o.resourcePacks);\n        mc.reloadResourcePacks();')
    return s

def mod_class(v262):
    setter = 'mc.gui.setScreen(new MixerScreen(mc.gui.screen()))' if v262 else 'mc.setScreen(new MixerScreen(mc.screen))'
    return '''package dev.packmixer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PackMixerMod implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.packmixer.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("packmixer", "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.consumeClick()) %s;
            long d = Library.dirtyAt;
            if (d > 0 && System.currentTimeMillis() - d > 600) { Library.dirtyAt = 0; Library.compose(mc); }
        });
    }
}
''' % setter

TARGETS = {'26.1.2': dict(v262=False, pf=84, mc='~26.1', fapi='0.155.2+26.1.2', mm='18.0.2'),
           '26.2':   dict(v262=True,  pf=88, mc='~26.2', fapi='0.161.0+26.2',   mm='20.0.3')}
screen_src = open(f'{SRC}/MixerScreen.java').read(); lib_src = open(f'{SRC}/Library.java').read()
for ver, t in TARGETS.items():
    root = f'mc-{ver}'; jd = f'{root}/{SRC}'; rd = f'{root}/{RES}'
    shutil.rmtree(root, ignore_errors=True); os.makedirs(jd); shutil.copytree(RES, rd)
    ms = port_screen(screen_src, t['v262']); lb = port_library(lib_src, t['pf'])
    open(f'{jd}/MixerScreen.java', 'w').write(ms); open(f'{jd}/Library.java', 'w').write(lb)
    open(f'{jd}/PackMixerMod.java', 'w').write(mod_class(t['v262']))
    for f in ('Ui.java', 'ModMenuIntegration.java'): shutil.copy(f'{SRC}/{f}', jd)
    fm = json.load(open(f'{rd}/fabric.mod.json'))
    fm['depends'] = {"fabricloader": ">=0.18.4", "minecraft": t['mc'], "java": ">=25", "fabric-api": "*"}
    json.dump(fm, open(f'{rd}/fabric.mod.json', 'w'), indent=2)
    open(f'{root}/settings.gradle', 'w').write("pluginManagement { repositories { maven { url = 'https://maven.fabricmc.net/' }; mavenCentral(); gradlePluginPortal() } }\n")
    open(f'{root}/gradle.properties', 'w').write(f'''org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false
minecraft_version={ver}
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version={t['fapi']}
modmenu_version={t['mm']}
mod_version=1.0.0
archives_base_name=pack-mixer-{ver}
''')
    open(f'{root}/build.gradle', 'w').write('''plugins { id 'net.fabricmc.fabric-loom' version "${loom_version}" }
version = project.mod_version
base { archivesName = project.archives_base_name }
repositories {
  maven { url = 'https://maven.terraformersmc.com/'; metadataSources { mavenPom() }; content { includeGroup 'com.terraformersmc' } }
}
dependencies {
  minecraft "com.mojang:minecraft:${project.minecraft_version}"
  implementation "net.fabricmc:fabric-loader:${project.loader_version}"
  implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
  compileOnly("com.terraformersmc:modmenu:${project.modmenu_version}") { transitive = false }
}
tasks.withType(JavaCompile).configureEach { options.release = 25 }
java { sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
processResources { inputs.property "version", project.version; filesMatching("fabric.mod.json") { expand "version": project.version } }
''')
print('generated', list(TARGETS))
