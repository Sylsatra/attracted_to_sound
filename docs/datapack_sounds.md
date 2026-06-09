# Data-driven sounds in Sound Attract

This describes how datapacks configure which sounds mobs can hear and how strong they are.

## Where JSON files are loaded from

Sound Attract registers a `SimpleJsonResourceReloadListener` on the folder:

- `data/*/sounds/*.json`

Any namespaced datapack can contribute files there. Typical locations:

- `data/soundattract/sounds/*.json` (your own datapack or built-in defaults)
- `data/<other_mod>/sounds/*.json` (integration datapacks from other mods)

All of these files are merged together.

## JSON schema

Each file must be a JSON object with the following optional fields:

```jsonc
{
  "replace_whitelist": false,
  "replace_defaults": false,
  "whitelist": [
    "namespace:sound_id",
    "#namespace:sound_tag"
  ],
  "defaults": {
    "namespace:sound_id": { "range": 16.0, "weight": 1.0 },
    "#namespace:sound_tag": { "range": 24.0, "weight": 2.0 }
  }
}
```

- `replace_whitelist` (bool, default `false`)
  - If `true` in any file, the accumulated datapack whitelist is cleared **before** applying that file.
- `replace_defaults` (bool, default `false`)
  - If `true` in any file, the accumulated datapack defaults map is cleared **before** applying that file.
- `whitelist` (array)
  - Each entry is either a sound ID or a sound tag:
    - `"namespace:sound"` → that exact sound event.
    - `"#namespace:tag"` → all sounds in that sound tag.
- `defaults` (object)
  - Keys are sound IDs or tags (same format as `whitelist`).
  - Values are objects with:
    - `range` (double): hearing range in blocks.
    - `weight` (double): relative importance when mobs choose between multiple sounds.

All sound tags used here must be defined separately as normal sound tags.

## Interaction with TOML config

TOML still defines:

- `soundIdWhitelist` list.
- `rawSoundDefaults` list (sound → range/weight).

At runtime, Sound Attract builds two internal caches:

- `SOUND_ID_WHITELIST_CACHE`
- `SOUND_DEFAULT_ENTRIES_CACHE`

The datapack values are first loaded into separate caches:

- `DP_SOUND_WHITELIST_CACHE`
- `DP_SOUND_DEFAULTS_CACHE`

Then `SoundAttractConfig.bakeConfig()` merges config + datapacks into the final caches using:

- `enableDataDriven` (bool)
- `datapackPriority` (`"datapack_over_config"` or `"config_over_datapack"`)

### Merging rules

- If `enableDataDriven = false` or a datapack cache is empty, only TOML values are used.
- If `enableDataDriven = true` and datapacks provide values:

  **Whitelist**
  - `datapackPriority = "datapack_over_config"`:
    - The whitelist from datapacks replaces the TOML whitelist.
  - `datapackPriority = "config_over_datapack"`:
    - Final whitelist = TOML entries ∪ datapack entries.

  **Defaults**
  - Always merged per-sound-ID:
    - `datapack_over_config` → datapack overrides TOML for that sound.
    - `config_over_datapack` → TOML value wins; datapack only fills missing entries.

## Multiple datapacks and namespaces

All files under `data/*/sounds/*.json` are processed together:

- Different namespaces (e.g. `soundattract`, `tacz`, `mymod`) can all contribute sound entries.
- If two datapacks define **different** sound IDs, the final result is simply the union of all entries.
- If two datapacks define defaults for the **same** sound ID, the last one loaded wins within the datapack layer.
  - You can use normal datapack priority (pack order) plus `replace_*` flags to control this.

After all datapacks are merged into `DP_*` caches, TOML is applied on top or underneath according to `datapackPriority`.

## Reload behavior

- Datapacks are loaded on world load and `/reload`.
- After the JSONs are processed, `SoundAttractConfig.bakeConfig()` is called again so that the final whitelist/defaults caches reflect the latest datapacks.

This means you can:

- Ship add-on datapacks that define new sounds without touching `soundattract-common.toml`.
- Combine multiple integration datapacks (each in its own namespace) and let Sound Attract build one effective sound behavior map.

## Other data-driven features (overview)

Sound Attract also exposes several other behavior surfaces via datapacks and tags. They all follow the same high-level pattern:

- Datapacks populate dedicated `DP_*` caches (or tags).
- `SoundAttractConfig.bakeConfig()` merges TOML + datapacks using `enableDataDriven` and `datapackPriority`.
- Multiple datapacks can contribute entries; different keys are merged, and conflicts are resolved by `datapackPriority` and normal datapack pack order.

### Mobs: attracted / blacklisted

- **Where**: vanilla entity tags
  - `data/*/tags/entity_type/attracted.json`
  - `data/*/tags/entity_type/blacklist.json`
- **Usage**:
  - Combined with TOML `attractedEntities` / `mobBlacklist` in `SoundAttractionEvents`.
  - If `enableDataDriven = false` → only TOML.
  - If `enableDataDriven = true`:
    - `datapack_over_config` → tags replace TOML sets.
    - `config_over_datapack` → final set = TOML ∪ tags.

### Camouflage armor & armor colors

- **Camouflage armor items**:
  - Tag: `data/*/tags/item/camouflage_armor.json`.
  - Combined with TOML `camouflageArmorItems` via `CamoUtil.isCamouflageArmorItem`.
- **Armor colors (environmental camouflage)**:
  - JSON: `data/*/camo/armor_colors/*.json`.
  - Populates `DP_CUSTOM_ARMOR_COLORS`, then merged into `customArmorColors`.
- **Merging**:
  - `enableDataDriven = false` → only TOML list.
  - `enableDataDriven = true`:
    - `datapack_over_config` → datapack entries replace config map.
    - `config_over_datapack` → datapack only fills missing items.

### Block muffling & non-blocking vision

- **Muffling categories (wool/solid/non_solid/thin/liquid/air)**:
  - Tags under `data/*/tags/block/muffling/*.json` (e.g. `muffling/wool.json`).
  - Combined with TOML `custom*Blocks` lists in `SoundTracker` helpers
    (`isCustomWool`, `isCustomSolid`, etc.).
  - Effective category = config list ∪ tag membership.
- **Non-blocking vision**:
  - Tag: `data/*/tags/block/vision/non_blocking.json`.
  - Combined with TOML `nonBlockingVisionAllowList` in `FovEvents.isNonBlockingVision`.
- **Merging**:
  - Tags are additive; we effectively treat “config OR tag” as allowed when `enableDataDriven = true`.

### Mob / player profiles

- **Where**:
  - Mob profiles: `data/*/profiles/mobs/*.json`.
  - Player profiles: `data/*/profiles/players/*.json`.
- **Schema (per file)**:
  - Root:
    - `replace` (bool, optional) – if true, clears previously loaded datapack profiles before applying this file.
    - `profiles`: array of profile objects.
  - Mob profile object (summary): `name`, `mob` (entity ID or `"*"`), optional `nbt`, 
    `sound_overrides` (list of `{ sound, range, weight }`), `detection_overrides` (`standing` / `sneaking` / `crawling`).
  - Player profile object (summary): `name`, optional `nbt`, `detection_overrides`.
- **Merging**:
  - All datapack profiles are concatenated into `DP_MOB_PROFILES_CACHE` / `DP_PLAYER_PROFILES_CACHE`.
  - `enableDataDriven = false` → only TOML profile strings are used.
  - `enableDataDriven = true`:
    - `datapack_over_config` → only datapack profiles are used.
    - `config_over_datapack` → final profile list = config profiles ∪ datapack profiles.

### TACZ & Point Blank gun data

- **Where**:
  - TACZ: `data/*/guns/tacz/*.json`.
  - Point Blank: `data/*/guns/pointblank/*.json`.
- **TACZ JSON (per file)**:
  - Root:
    - `replace` (bool, optional) – if true, clears previous TACZ DP caches before this file.
    - `guns`: array of `{ item, db, weight? }`.
    - `attachments`: array of `{ item, db }`.
    - `muzzle_flash`: array of `{ item, reduction }`.
- **Point Blank JSON (per file)**:
  - Root:
    - `replace` (bool, optional).
    - `guns`: array of `{ item, range }`.
    - `attachments`: array of `{ item, reduction }`.
    - `muzzle_flash`: array of `{ item, reduction }`.
- **Merging**:
  - Within the datapack layer:
    - All files are applied in load order.
    - `replace = true` clears prior DP entries for that integration.
    - Last definition for a given item ID wins inside the DP map.
  - With config (`enableDataDriven` / `datapackPriority`):
    - `datapack_over_config` → datapack maps replace config entries on conflict.
    - `config_over_datapack` → config values win; datapacks fill missing guns/attachments.
