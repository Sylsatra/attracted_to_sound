# 🔊 Attract to Sound

> **Bring Realistic Sound Detection to Minecraft!**

---

## 🚀 Installation

Download:
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-orange?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/attract-to-sound)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-brightgreen?logo=modrinth)](https://modrinth.com/mod/attract-to-sound)

1. **Download the Mod** from one of the links above.
2. **Place the JAR file** into your `mods` folder.
3. **Requires Forge** (check your Minecraft version compatibility on the download page).
4. (Optional) Install [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) and/or [TacZ Guns](https://www.curseforge.com/minecraft/mc-mods/tacz-guns) for integration features.

---

## 🎮 How to Play

- **Sound-Based AI:**
  - Most hostile mobs (and any you add in the config) are attracted to sounds you make: walking, running, sneaking, jumping, breaking blocks, opening doors, firing guns, and more.
  - Each sound has its own range and "weight"—louder actions draw mobs from farther away.

- **Voice Chat Integration:**
  - If [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) is installed, mobs can detect and be attracted to your real voice. Whispering has a shorter range than normal talking.

- **TacZ Gun Integration:**
  - If [TacZ Guns](https://www.curseforge.com/minecraft/mc-mods/tacz-guns) is installed, gunshots and reloads create powerful sound events that can attract mobs from great distances.

- **Block Muffling:**
  - Certain blocks (wool, glass, doors, and any you configure) can reduce the range and intensity of sounds, letting you build soundproof rooms or stealthy passageways.

- **Mob Grouping & Smart AI:**
  - Mobs can form groups, follow leaders, and use smart edge detection for more realistic investigation and hunting behavior.

- **Camouflage & Stealth:**
  - Wear a full set of dyed armor and stand, sneak, or crawl near matching blocks to reduce mob detection range.
  - The more matching blocks (up to 6 adjacent for standing/sneaking), the stronger the effect (quadratic scaling).
  - Crawling on a matching block gives full camouflage.
  - Camouflage works with both vanilla and modded blocks/armor, and is fully customizable in the config.

### Example Scenarios
- Lure zombies away from your base by throwing a snowball or firing a gun.
- Use voice chat to distract mobs (or prank your friends).
- Build soundproof rooms with wool or custom blocks to hide from mobs.
- Crawl through tall grass or colored wool to sneak past enemies undetected.
- Set up traps and distractions using sound mechanics for creative mob control.

---

## ⚙️ Configuration

**Config file:** `config/soundattract-common.toml`

Open this file in a text editor (while Minecraft is closed) to fine-tune every aspect of the mod. Each option is documented in the config file itself. Below is a summary of key options:

### 1. General
- **debugLogging**: Enable aggressive debug logging for the mod.
- **soundLifetimeTicks**: How long (in ticks) a sound remains interesting (20 ticks = 1 second).
- **scanCooldownTicks**: How often (in ticks) mobs scan for new sounds.
- **minTpsForScanCooldown / maxTpsForScanCooldown**: TPS bounds for scan cooldown scaling.
- **arrivalDistance**: Distance (in blocks) at which a mob is considered to have reached the sound.
- **mobMoveSpeed**: Speed modifier for mobs moving toward a sound (1.0 = normal).
- **soundSwitchRatio**: How easily mobs switch between sound targets (lower = easier to switch).

### 2. Mobs & Grouping
- **attractedEntities**: List of mobs that will be attracted to sounds. Example: `["minecraft:zombie", "minecraft:skeleton"]`
- **edgeMobSmartBehavior**: Experimental feature for smarter mob group edge detection.
- **groupDistance**: Max distance for mobs to be considered in a group.
- **maxLeaders**: Maximum number of group leaders.
- **maxGroupSize**: Maximum number of mobs in a group.
- **leaderSpacingMultiplier**: Multiplier for leader spacing.
- **leaderGroupRadius**: (Currently unused, default 32.0)
- **numEdgeSectors**: Number of edge sectors for mob selection.

### 3. Sound Events & Whitelist
- **nonPlayerSoundIdList**: List of non-player sound IDs that mobs can be attracted to. Format: `soundId;range;weight`.
- **soundIdWhitelist**: Whitelist of sound IDs to process for performance.

### 4. Muffling & Block Settings
- **mufflingAreaRadius**: Radius (in blocks) around the ray to check for muffling blocks.
- **woolMufflingEnabled, solidMufflingEnabled, nonSolidMufflingEnabled, thinMufflingEnabled, liquidMufflingEnabled**: Toggle muffling types on/off.
- **customWoolBlocks, customSolidBlocks, customNonSolidBlocks, customThinBlocks, customLiquidBlocks**: Add block IDs for custom muffling.
- **woolBlockRangeReduction, woolBlockWeightReduction**: Wool block muffling strength.
- **solidBlockRangeReduction, solidBlockWeightReduction**: Solid block muffling strength.
- **nonSolidBlockRangeReduction, nonSolidBlockWeightReduction**: Non-solid block muffling strength.
- **thinBlockRangeReduction, thinBlockWeightReduction**: Thin block muffling strength.
- **liquidBlockRangeReduction, liquidBlockWeightReduction**: Liquid block muffling strength.

### 5. Detection & Camouflage
- **baseDetectionRange**: Base detection range for mobs.
- **sneakDetectionRange**: Detection range when sneaking.
- **crawlDetectionRange**: Detection range when crawling.
- **standingDetectionRange**: Detection range when standing.
- **sneakDetectionRangeCamouflage, crawlDetectionRangeCamouflage, standingDetectionRangeCamouflage**: Detection range when camouflaged (by stance).
- **camouflageSets**: List of camouflage sets. Format: `color;helmet;chestplate;leggings;boots[;block1;block2;...]`.
    - Example (green): `5E7C16;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:green_wool;minecraft:green_terracotta;minecraft:moss_block;minecraft:grass_block;minecraft:leaves`
    - You can add modded blocks and armor pieces as needed.
    - See below for logic details.

#### Camouflage Logic (Updated)
- **Standing/Sneaking:**
    - If you wear a full matching armor set dyed to a configured color, and are adjacent to matching blocks, your detection range is reduced.
    - The reduction is now **quadratic**: the more matching adjacent blocks (up to 6), the much stronger the camouflage effect. For example, 3/6 matches gives 25% camo, 6/6 gives 100%.
    - Formula: `effectiveRange = base - (base - camo) * (matches/6)^2`
- **Crawling:**
    - If you crawl on a matching block (directly below you), you get full camouflage for that set.
    - Otherwise, no camouflage bonus is applied.
- **Partial Coverage:**
    - Partial camouflage is possible for standing/sneaking, but the effect increases rapidly as you add more matching blocks.
- **Configuration:**
    - All camouflage sets, colors, armor, and blocks are fully customizable in the config file.

### 6. Voice Chat & TaCz Integration
- **enableVoiceChatIntegration**: Toggle voice chat detection.
- **voiceChatWhisperRange, voiceChatNormalRange**: Ranges (in blocks) for whisper/normal voice chat.
- **voiceChatWeight**: Weight of voice chat sounds (how attractive to mobs).
- **enableTaczIntegration**: Toggle TaCz gun sound detection.
- **taczReloadRange, taczReloadWeight**: Range/weight for gun reload sounds.
- **taczShootRange, taczShootWeight**: Range/weight for gun shoot sounds.
- **taczGunShootDecibels**: List of TaCz gun shoot decibel levels. Format: `soundId;decibel`.
- **taczAttachmentReductions**: List of TaCz gun attachment decibel reductions. Format: `soundId;reduction`.

### 7. Parcool Animator Integration
- **parcoolAnimatorSounds**: List of Parcool animator sound configs. Format: `AnimatorClass;soundId;range;weight;volume;pitch`.

---
**Tip:**
For the most up-to-date and detailed documentation, always refer to the comments in your generated `soundattract-common.toml` file.

---

## ➕ Adding Modded Mobs, Blocks, and Sounds

### Add Modded Mobs
- Add their entity ID to `attractedEntities` in the config.
  ```toml
  attractedEntities = [
    "minecraft:zombie",
    "alexsmobs:grizzly_bear",
    "yourmod:custom_mob"
  ]
  ```

### Add Custom Blocks for Muffling
- Add block IDs to `customWoolBlocks`, `customSolidBlocks`, etc.
  ```toml
  customWoolBlocks = ["minecraft:wool", "moddedwool:blue_wool"]
  ```

### Add Custom Sounds
- Add sound event IDs to `soundConfigs` with range and weight.
  ```toml
  soundConfigs = ["yourmod:alarm;40;10"]
  ```

---

## 🔫 TacZ Gun Integration

- When [TacZ Guns](https://www.curseforge.com/minecraft/mc-mods/tacz-guns) is installed and enabled, gunshots and reloads generate sound events.
- Configure each gun’s loudness in `taczGunShootDecibels`:
  ```toml
  taczGunShootDecibels = ["tacz:ak47;158.9"]
  ```
- Attachments can reduce (or increase) sound using `taczAttachmentReductions`:
  ```toml
  taczAttachmentReductions = ["tacz:suppressor;35"]
  ```
- You can add support for new guns or attachments from other mods using the same format.

---

## 🛠️ Advanced: Customizing AI & Sound Logic

- Tweak how long sounds linger (`soundLifetimeTicks`), how often mobs scan for noise (`scanCooldownTicks`), how far they’ll chase, and how fast they move.
- Fine-tune block muffling, per-sound weights, group AI, camouflage, and more for a unique experience.

---

## 📸 Media & Links

[![Watch the Demo](https://img.youtube.com/vi/p0MdSiWyYg0/hqdefault.jpg)](https://www.youtube-nocookie.com/embed/p0MdSiWyYg0)

![Sound Attract Thumbnail](https://media.forgecdn.net/attachments/1163/486/thumbnail-jpg.jpg)

![Mob Attraction Demo](https://media.forgecdn.net/attachments/1163/487/screenshot-2025-04-20-140635-png.png)

- [CurseForge Project](https://www.curseforge.com/minecraft/mc-mods/attract-to-sound)
- [Modrinth Project](https://modrinth.com/mod/attract-to-sound)

---

## ❓ FAQ

- **Q: Does this work with modded mobs and blocks?**
  - A: Yes! Just add their IDs in the config.
- **Q: Can I disable certain features?**
  - A: Everything is toggleable in the config file.
- **Q: Is it compatible with multiplayer?**
  - A: Yes, works on both singleplayer and servers.
- **Q: Can I add this mod to my modpack?**
  - A: Yes, you can add it to your modpack.

---

## 🧑‍💻 Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you’d like to change.

---

## © License

Distributed under the GNU General Public License v3.0. See `LICENSE` for more information.

---

*Drop the beat. Fine-tune the config. And let the mobs come to you.* 🎶🧟‍♂️
