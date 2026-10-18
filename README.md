# Pack Mixer (Fabric, Minecraft 1.21.11, client-only)

**Use:** press **K** in game, or open **Mods > Pack Mixer > Configure** (needs Mod Menu).
Click a card to apply it. Drop a resource-pack .zip onto the window to import it; it is split into per-collection styles.

**Mods you need:** Fabric Loader 0.18+, Fabric API for 1.21.11, and Mod Menu 17.x if you want it listed in the in-game mod list.

**Build (no local setup):** push this folder to a GitHub repo; the Actions tab builds the jar (artifact `pack-mixer`).
**Build locally:** JDK 21 + Gradle 8.14+, then `gradle build`; jar is `build/libs/pack-mixer-1.0.0.jar` (not the -sources jar).
