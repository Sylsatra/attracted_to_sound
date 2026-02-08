# Sound Attract Example Datapack

This datapack demonstrates how to use the `MobProfile2` and `PlayerProfile2` formats introduced in version 5.0.0.

## Directories

- `data/soundattract/mob_profiles/`: JSON files defining custom behavior for specific mobs.
- `data/soundattract/player_profiles/`: JSON files defining how players are detected or emit scent based on conditions.

## EntityPredicate in Detail

Both Mob and Player profiles use standard Minecraft **Entity Predicates** for their `condition` field. This allows you to target entities with extreme precision based on their NBT, equipment, distance, effects, and more.

### Key Features
- **Type**: Match by entity type (e.g., `"minecraft:zombie"`).
- **NBT**: Match by complex NBT data (e.g., `"{BlindTracker:1b}"` or Origins-specific data).
- **Equipment**: Match based on what the entity is wearing or holding.
- **Effects**: Match based on active status effects.
- **Distance**: Match based on distance to the player or a point.

### Documentation & Links
- **Official Documentation**: [Minecraft Wiki - Entity Predicate](https://minecraft.wiki/w/Entity_predicate)
- **Examples**:
  - `{"type": "minecraft:skeleton"}`: Matches all skeletons.
  - `{"nbt": "{IsBoss:1b}"}`: Matches entities with a custom NBT tag.
  - `{"location": {"biome": "minecraft:plains"}}`: Matches entities in Plains biomes.

---

## Mob Profile Format (`mob_profiles/`)

Used to customize how specific mobs react to sounds and track scents.

### Example: Blind Tracker Zombie
Located in `data/soundattract/mob_profiles/zombie_example.json`.
- **Blindness**: Setting `detection_overrides` to `0.0` makes the zombie unable to see players in those stances.
- **Sound Sensitivity**: Adding `sound_overrides` for `soundattract:player_action.*` makes it hear movements from much further away and prioritize them.
- **Keen Smell**: Increased `detection_range` and configured `ambush_duration_ticks` in `scent_config`.

---

## Player Profile Format (`player_profiles/`)

Used to customize how players are detected or how they leave scent trails.

### Example: Feline Origin
Located in `data/soundattract/player_profiles/sneaky_player.json`.
- **Condition**: Uses a complex NBT check for the **Origins** mod to detect players with the "Feline" origin.
- **Detection Overrides**: Specific base detection ranges for standing, sneaking, and crawling.
- **Scent Emission**: Reduced scent footprint to match a stealthy character.
