# Pack Mixer
Three builds from one repo (client-only Fabric mod, press K in game):
* root folder: Minecraft 1.21.11 (Yarn names, Java 21)
* mc-26.1.2/: Minecraft 26.1.x (Mojang names, Java 25)
* mc-26.2/: Minecraft 26.2.x (Mojang names, Java 25)

Push to GitHub: Actions builds all three jars (artifacts pack-mixer-1.21.11, -26.1.2, -26.2).
Needs Fabric API, plus Mod Menu if you want it in the mod list.
Collections: edit and run `python3 tools/gen_collections.py`. The 26.x sources are generated from src/ by `python3 tools/port_to_26.py`.
