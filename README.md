# 🔊 Attract to Sound – Realistic Mob Sound Detection Mod \[Forge & Fabric\]

**Enhance Minecraft’s AI with truly reactive mobs—now supporting multiple gun, voice-chat, and animation mods!**

Attract to Sound transforms hostile mob behavior by making them respond dynamically to in-game noises: footsteps, block interactions, voice chat, gunshots, and more. Perfect for players who crave immersive stealth, strategic combat, and configurable AI challenges.

***

## ✨ Key Features

*   **Realistic Sound-Based Mob AI** Hostile mobs actively hunt sound sources: walking, running, breaking/placing blocks, opening doors/chests, attacking, and more.
    
*   **Configurable Sound Ranges & Weights** For each sound event, define a detection **range** (how far away mobs hear it) and a **weight** (how “loud”/urgent it is). Louder events (explosions, gunshots) draw mobs from farther distances.
    
*   **Sound Muffling & Block Attenuation** Use wool, glass, doors, or any custom blocks to reduce both range and intensity of passing sounds. Build soundproof rooms, stealth corridors, or hidden tunnels.
    
*   **Dynamic Camouflage System** Wear dyed armor sets and stand on matching blocks to greatly reduce detection.
    
    *   **Standing/Sneaking:** Detection range scales quadratically based on adjacent matching blocks (up to 6).
    *   **Crawling:** Maximum camouflage when directly on a matching block. Fully configurable for vanilla and modded armor/blocks.
*   **Advanced Mob Grouping & Pathfinding** Mobs no longer simply run toward noise—they form investigation groups, elect leaders, and use “smart edge” scouts to decide whether a peripheral sound is worth rallying the entire pack.
    
*   **Highly Customizable** Every aspect (AI scan rates, sound lifetimes, camouflage weights, muffling effectiveness, grouping parameters, etc.) is exposed in `config/soundattract-common.toml`.
    
*   **Server-Side & Multiplayer Compatible** All logic runs server-side. Works in singleplayer and on any Forge/Fabric server without client-side hacks.
    

***

## 🔌 Supported Integrations

To maximize immersion, Attract to Sound includes optional hooks for these popular mods and datapacks. Simply install them alongside Attract to Sound and enable integration in the config:

1.  **Simple Voice Chat**
    
    *   Detects your actual microphone input as in-game “voice” events.
    *   Whisper vs. normal talk have different ranges/weights.
    *   ModID: `simplevoicechat`
    *   Download: [https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat)
2.  **TacZ Guns** + **Suffuse – Gunsmoke** Datapack
    
    *   Gunshots and reloads become decibel-based sound events.
    *   Configure each weapon’s loudness and attachment decibel reductions.
    *   ModID: `tacz` (uses Suffuse datapack for JSON decibel parsing)
    *   TacZ Guns: [https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)
    *   Suffuse – Gunsmoke Datapack: [https://www.curseforge.com/minecraft/customization/suffuse-gunsmoke](https://www.curseforge.com/minecraft/customization/suffuse-gunsmoke)
3.  **Scorched Guns**
    
    *   Adds futuristic firearms—each fire/reload sound can attract mobs.
    *   ModID: `scorchedguns`
    *   Download: [https://www.curseforge.com/minecraft/mc-mods/scorched-guns](https://www.curseforge.com/minecraft/mc-mods/scorched-guns)
4.  **Ewewukek’s Musket Mod**
    
    *   Black-powder muskets and flintlocks produce loud “blast” and “reload” events.
    *   ModID: `musketmod`
    *   Download: [https://www.curseforge.com/minecraft/mc-mods/ewewukeks-musket-mod](https://www.curseforge.com/minecraft/mc-mods/ewewukeks-musket-mod)
5.  **MrCrayfish’s Gun Mod (Unofficial)**
    
    *   Modern handguns, rifles, shotguns with distinct firing/reloading sounds.
    *   ModID: `gunmod` (or sometimes `mrcrayfishgunmod`)
    *   Download: [https://www.curseforge.com/minecraft/mc-mods/mrcrayfishs-gun-mod-unofficial](https://www.curseforge.com/minecraft/mc-mods/mrcrayfishs-gun-mod-unofficial)
6.  **Parcool Animator**
    
    *   Tie Parcool character animations (dash, roll, slide) to custom sound events.
    *   ModID: `parcool`
    *   Download: [https://www.curseforge.com/minecraft/mc-mods/parcool](https://www.curseforge.com/minecraft/mc-mods/parcool)

**Plus Any Other Mod with Custom Sounds or Entities**

*   Add their sound event IDs to `soundIdWhitelist` and `rawSoundDefaults`.
*   Add custom entity IDs to `attractedEntities`.
*   Define **Mob Profiles** for elite or variant behaviors via `specialMobProfilesRaw`.

***

## 🚀 Installation Guide

1.  **Download the Mod**
    
    *   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/attract-to-sound) • [Modrinth](https://modrinth.com/mod/attract-to-sound)
2.  **Install Minecraft Forge or Fabric**
    
    *   Ensure compatibility with the mod’s supported Minecraft version (check project page).
3.  **Place the JAR**
    
    *   Copy `attract-to-sound-<version>.jar` into your `mods/` folder.
4.  **Launch Minecraft**
    
    *   Select the Forge/Fabric profile and start the game.
5.  **(Optional) Install Integrations**
    
    *   Place additional mod JARs (e.g., Simple Voice Chat, TacZ Guns, Scorched Guns, etc.) into `mods/`.
    *   If using TacZ Guns, also install the **Suffuse – Gunsmoke** datapack in your world’s `datapacks/` folder.

***

## ⚙️ Configuration Overview

Configuration is handled in `config/soundattract-common.toml`. Close Minecraft before editing; each setting is commented in-file. Key sections include:

1.  **General AI Settings**
    
    *   `debugLogging`, `soundLifetimeTicks`, `scanCooldownTicks`, `arrivalDistance`, `mobMoveSpeed`, `soundSwitchRatio`, etc.
2.  **Mob Configuration & Grouping AI**
    
    *   `attractedEntities`: list of all entity IDs (vanilla + modded) that react to sound.
    *   `edgeMobSmartBehavior`, `groupDistance`, `maxGroupSize`, `maxLeaders`, `leaderSpacingMultiplier`, `groupAssignInterval`.
3.  **Sound Event Definitions & Whitelist**
    
    *   `soundIdWhitelist`: optional list of sound IDs to process (performance optimization).
    *   `nonPlayerSoundIdList` / `rawSoundDefaults`: define custom sound events in format `sound_id;range;weight`.
4.  **Block Muffling Configuration**
    
    *   `mufflingAreaRadius`, `woolMufflingEnabled`, `solidMufflingEnabled`, etc.
    *   `customWoolBlocks`, `customSolidBlocks`, `customThinBlocks`, etc., to include modded mufflers.
    *   `woolBlockRangeReduction`, `solidBlockWeightReduction`, and other reduction factors.
5.  **Detection & Camouflage**
    
    *   Stance-based detection: `standingDetectionRange`, `sneakDetectionRange`, `crawlDetectionRange` and their camo variants.
    *   `camouflageSets`, `camouflagePartialMatching`, `camouflageArmorPieceWeight`, `camouflageBlockMatchWeight`, `camouflageDistanceScaling`, etc.
    *   Environmental camouflage: `enableEnvironmentalCamouflage`, `envColorSampleRadius`, `environmentalMismatchPenaltyFactor`, etc.
    *   Movement & stealth modifiers: `movementStealthPenalty`, `stationaryStealthBonusFactor`, `invisibilityStealthFactor`.
    *   Lighting & weather factors: `neutralLightLevel`, `lightLevelSensitivity`, `rainStealthFactor`, `thunderStealthFactor`.
6.  **Integrations**
    
    *   Voice Chat: `enableVoiceChatIntegration`, `voiceChatWhisperRange`, `voiceChatNormalRange`, `voiceChatWeight`.
    *   TacZ Guns: `enableTaczIntegration`, `taczShootRange`, `taczShootWeight`, `taczReloadRange`, `taczReloadWeight`, `taczGunShootDecibels`, `taczAttachmentReductions`.
    *   Parcool: `parcoolAnimatorSounds` (format `AnimatorClass;soundId;range;weight;volume;pitch`).
7.  **Mob Profiles (Advanced Overrides)**
    
    *   `specialMobProfilesRaw`: override sound/detection per-entity (and conditional on NBT). Format:
    
    ```
     "profileName;mobId;nbtMatcher;soundOverrides;detectionOverrides"
    ```
    
    *   Example:
    
    ```
     specialMobProfilesRaw = [
       "AlphaBear;alexsmobs:grizzly_bear;{IsAlpha:1b};minecraft:entity.player.hurt:80.0:5.0;standing:90.0,sneaking:45.0,crawling:25.0"
     ]
    ```
    

***

## 🎮 Supported Mods & Datapacks

*   **Simple Voice Chat** (modid: `simplevoicechat`)
    
    *   Mobs hear real voice chat (whisper vs. normal talk).
*   **TacZ Guns** (modid: `tacz`) + **Suffuse – Gunsmoke** datapack
    
    *   Gunshots and reloads generate decibel-based sound events.
*   **Scorched Guns** (modid: `scorchedguns`)
    
    *   Futuristic firearms; each shot and reload attracts mobs.
*   **Ewewukek’s Musket Mod** (modid: `musketmod`)
    
    *   Black-powder muskets and flintlocks; loud blast and reload noise.
*   **MrCrayfish’s Gun Mod (Unofficial)** (modid: `gunmod`)
    
    *   Modern handguns, rifles, and shotguns.
*   **Parcool** (modid: `parcool`)
    
    *   Custom animations (dash, roll) trigger stealth-based sounds.
*   **Any Other Mod**
    
    *   Add custom sound IDs to `soundIdWhitelist`/`rawSoundDefaults`.
    *   Add custom entity IDs to `attractedEntities`.
    *   Define Mob Profiles for elite or variant behaviors.

***

## 🔗 Download & Links

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-orange?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/attract-to-sound) [![Modrinth](https://img.shields.io/badge/Modrinth-Download-brightgreen?logo=modrinth)](https://modrinth.com/mod/attract-to-sound)

*   **Issues & Support:** • GitHub Issues: [https://github.com/Sylsatra/attract\_to\_sound/issues](https://github.com/Sylsatra/attract_to_sound/issues) • Modrinth Discussions / CurseForge Comments
    
*   **Community:** • Join our Discord (link on GitHub ReadMe) for real-time support and updates.
    

***

## ❓ FAQ Highlights

*   **Does it work with other-modded mobs?** Yes—just add their entity IDs to `attractedEntities` or define a Mob Profile.
    
*   **Can I disable voice chat or camouflage?** Yes—toggle `enableVoiceChatIntegration` or set `enableCamouflage = false` in the config.
    
*   **Multiplayer compatible?** Absolutely. All logic runs server-side; client cannot override mob hearing.
    
*   **Performance impact?** Mod is optimized with dynamic scan cooldowns and caching. Adjust `scanCooldownTicks`, `maxSoundsTracked`, and grouping AI parameters to suit your TPS.
    
***

**Experience Minecraft like never before—your footsteps, voice, and every gunshot matter. Make some noise… and see who comes running!**
