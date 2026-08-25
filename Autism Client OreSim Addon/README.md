# OreSim Addon

An **AUTISM Client** addon that simulates vanilla ore generation from the world seed and displays ore positions in outlined boxes.

Ported from Nora Tweaks OreSim (originally adapted from Meteor Rejects) to the AUTISM Client API.

- **Mod name:** OreSim Addon (`oresim-addon`)
- **Author:** theflex5710
- **Target:** Minecraft 26.2 / Fabric Loader 0.19.3+ / AUTISM Client 4.4+

## Features

- Simulation for coal, iron, gold, redstone, lapis, copper, emerald, quartz, and ancient debris
- Uses the same `WorldgenRandom` call sequence as vanilla generation (identical results)
- Simulates chunks on load; positions are pruned when the server sends real block updates
- Automatically reloads on dimension or server change
- Seed persistence: auto-read in singleplayer; set manually via `.seed-world <seed>` for servers (saved per world/server in `.minecraft/config/autism/oresim-addon-seeds.json`)

## Settings

| Setting | Description |
|---|---|
| Chunk Range | Radius (in chunks) around the player to render (1–16) |
| Air Check | Air exposure validation: `On Load` / `Recheck` / `Off` |
| Ores | Toggle individual ore types (under the **Ores** group) |

## Command

```
.seed-world                → show the stored seed for the current world
.seed-world <seed>         → store a seed for the current server
.seed-world list           → list all stored seeds
.seed-world delete         → delete the stored seed for the current world
```

String seeds are converted to numbers via `String.hashCode` (same rule as vanilla).

## Installation

1. [AUTISM Client](https://modrinth.com/mod) 4.4+ installed (Fabric Loader 0.19.3+, Fabric API 0.152.2+)
2. Place `build/libs/OreSim-Addon-1.0.0-26.2.jar` into `.minecraft/mods`
3. Open the module menu in AUTISM and enable **OreSim Addon > OreSim**

If no seed is stored when you enable the module, a chat reminder will prompt you to run `.seed-world`.

## Building

Requirements: JDK 25, Gradle wrapper included.

```powershell
# 1) Publish the AUTISM Client artifact to mavenLocal (once):
cd ..\Autism-Client
.\gradlew.bat publishToMavenLocal --no-daemon

# 2) Build the addon:
cd ..\"OreSim Addon"
.\gradlew.bat build --no-daemon
```

Output: `build/libs/OreSim-Addon-<version>-26.2.jar`

Project layout:

```
src/main/java/com/theflex5710/oresim/
├── OreSimAddon.java        # autism entrypoint (extends AutismAddon)
├── OreSimInit.java         # fabric client entrypoint
├── commands/SeedCommand    # .seed-world command
├── mixin/                  # 3 placement accessors + LevelRenderer render hook
├── modules/OreSimModule    # core module (simulation + event handling)
├── render/OreSimRenderer   # box drawing (AutismWorldGeometry)
└── utils/{Ore,SeedStore}   # vanilla ore config reader + seed persistence
```

## Credits

- [Meteor Rejects](https://github.com/AntiCope/meteor-rejects/) — original OreSim concept and vein simulation code
- [Nora Tweaks](https://github.com/) — 26.2 port and placement accessor mixins (CC0)
- [AUTISM Client](https://github.com/) — addon API and rendering helpers

This project retains the attribution requested in the Meteor Rejects license header.
