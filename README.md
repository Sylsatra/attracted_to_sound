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

- **Mobs can now hear sounds:**
  - Walking, sprinting, sneaking, jumping, breaking blocks, opening doors, and more generate sound events.
  - Mobs listed in the config will investigate sounds based on their type and loudness.
- **Voice Chat Integration:**
  - If [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) is installed, mobs can detect your real voice.
- **TacZ Gun Integration:**
  - Gunshots and reloads from [TacZ: Timeless and Classics Zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) create powerful sound events.
- **Block Muffling:**
  - Wool, doors, glass, and custom blocks can reduce sound range and weight.
- **Stealth or Chaos:**
  - Sneak to minimize noise, or use sound to lure mobs into traps!

### Example Scenarios
- Lure zombies away from your base by throwing a snowball or firing a gun.
- Use voice chat to distract mobs (or prank your friends).
- Build soundproof rooms with wool or custom blocks.

---

## ⚙️ Configuration

**Config file:** `config/soundattract-common.toml`

Open this file in a text editor (while Minecraft is closed) to fine-tune every aspect of the mod. Each option is documented in the config file itself. Below is a summary, following the exact order and grouping of the config:

---
### 1. General
- **attractedEntities**: List of mobs that will be attracted to sounds.
  ```toml
  attractedEntities = ["minecraft:zombie", "alexsmobs:grizzly_bear"]
  ```
- **soundConfigs**: List of sounds that attract mobs. Format: `soundId;range;weight`.
  ```toml
  soundConfigs = ["minecraft:block.bell.use;30;5", "modid:custom_alarm;40;10"]
  ```
- **soundLifetimeTicks**: How long (in ticks) a sound remains interesting (20 ticks = 1 second).
- **scanCooldownTicks**: How often (in ticks) mobs scan for new sounds.
- **arrivalDistance**: Distance (in blocks) at which a mob is considered to have reached the sound.
- **mobMoveSpeed**: Speed modifier for mobs moving toward a sound (1.0 = normal).

---
### 2. Player Movement Sounds
- **enableSoundBasedDetection**: Enable sound detection for regular player steps.
- **enableMovementBasedDetection**: Enable detection for specific player actions (sneaking, crawling).
- **movementCheckFrequencyTicks**: How often to check player movement state.
- **playerStepSounds**: List of sound event IDs considered as player steps.
- **playerSpeedConfigs**: Player speed configs for step sounds. Format: `minSpeed;maxSpeed;range;weight`.
  ```toml
  playerSpeedConfigs = ["0.1;1.0;2;1", "1.51;4.5;8;1"]
  ```

---
### 3. Custom Block Muffling
- **woolBlockRangeReduction**, **woolBlockWeightReduction**: How much wool blocks reduce sound range/weight.
- **solidBlockRangeReduction**, **solidBlockWeightReduction**: How much solid blocks (not wool) reduce sound.
- **nonSolidBlockRangeReduction**, **nonSolidBlockWeightReduction**: How much non-solid blocks (like glass) reduce sound.
- **thinBlockRangeReduction**, **thinBlockWeightReduction**: How much thin blocks (panes, fences, etc.) reduce sound.
- **woolMufflingEnabled**: Toggle wool muffling on/off.
- **customWoolBlocks**, **customSolidBlocks**, **customNonSolidBlocks**, **customThinBlocks**: Add block IDs for custom muffling behavior.

---
### 4. Voice Chat Integration (Simple Voice Chat mod required)
- **enableVoiceChatIntegration**: Toggle voice chat detection.
- **voiceChatWhisperRange**, **voiceChatNormalRange**: Ranges (in blocks) for whisper/normal voice chat.
- **voiceChatWeight**: Weight of voice chat sounds (how attractive to mobs).

---
### 5. Sound Behavior
- **soundSwitchRatio**: The ratio at which mobs switch between sounds (higher = more likely to switch).

---
### 6. TaCz Gun Integration
- **enableTaczIntegration**: Toggle TaCz gun sound detection.
- **taczReloadRange**, **taczReloadWeight**: Range/weight for gun reload sounds.
- **taczShootRange**, **taczShootWeight**: Range/weight for gun shoot sounds.
- **taczGunShootDecibels**: List of TaCz gun shoot decibel levels. Format: `soundId;decibel`.
  ```toml
  taczGunShootDecibels = ["tacz:ak47;158.9"]
  ```
- **taczAttachmentReductions**: List of TaCz gun attachment decibel reductions. Format: `soundId;reduction`.
  ```toml
  taczAttachmentReductions = ["tacz:muzzle_silencer_phantom_s1;35"]
  ```

---
**Tip:**
For the most up-to-date and detailed documentation, always refer to the comments in your generated `soundattract-common.toml` file.

---

## ➕ Adding Modded Mobs, Blocks, and Sounds

### Add Modded Mobs
- Add their entity ID to `attractedEntities` in the config. Example:
  ```toml
  attractedEntities = [
    "minecraft:zombie",
    "alexsmobs:grizzly_bear",
    "yourmod:custom_mob"
  ]
  ```

### Add Custom Blocks for Muffling
- Add block IDs to `customWoolBlocks`, `customSolidBlocks`, etc. Example:
  ```toml
  customWoolBlocks = ["minecraft:wool", "moddedwool:blue_wool"]
  ```

### Add Custom Sounds
- Add sound event IDs to `soundConfigs` with range and weight. Example:
  ```toml
  soundConfigs = ["yourmod:alarm;40;10"]
  ```

---

## 🔫 TacZ Gun Integration

- When [TacZ Guns](https://www.curseforge.com/minecraft/mc-mods/tacz-guns) is installed and enabled, gunshots and reloads generate sound events.
- Configure each gun’s loudness in `taczGunShootDecibels`:
  ```toml
  taczGunShootDecibels = ["tacz:ak47;35", "tacz:m4a1;30"]
  ```
- Attachments can reduce (or increase) sound using `taczAttachmentReductions`:
  ```toml
  taczAttachmentReductions = ["tacz:suppressor;15"]
  ```
- You can add support for new guns or attachments from other mods using the same format.

---

## 🛠️ Advanced: Customizing AI & Sound Logic

- Tweak how long sounds linger (`soundLifetimeTicks`), how often mobs scan for noise (`scanCooldownTicks`), how far they’ll chase, and how fast they move.
- Fine-tune block muffling, per-sound weights, and more for a unique experience.

---

## 📸 Media & Links

<!-- YouTube video thumbnail link -->
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
