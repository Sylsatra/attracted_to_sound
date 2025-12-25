# Sound Attract

Sound Attract makes sound a real gameplay system. Mobs respond to events with range and weight, so stealth and noise become meaningful in both single player and multiplayer.

<a href="https://github.com/Admany/Quantified-API" rel="nofollow"><img src="https://i.imghippo.com/files/k1781Ug.png" alt="Quantified API Banner"></a>

## Overview
Sound Attract builds and maintains a sound map in each dimension and uses it to drive AI decisions. This keeps reactions consistent and configurable while preserving vanilla feel.

## Key features
- Sound sources with range, weight, and lifetime
- Per sound overrides and data driven tags
- Group behavior for more natural mob response
- Voice chat integration for live speech detection
- Hooks for popular combat and AI mods

## Quantified API integration
The mod uses Quantified API for scheduling and cache support. Heavy computations run off thread and results are applied on the main thread to keep Minecraft safe. This improves tick stability without changing gameplay logic.

## Configuration
All settings live in `config/soundattract-common.toml`.
- Attracted and blacklisted entities
- Sound ranges and weights
- Stealth and movement tuning
- Voice chat thresholds and ranges
- Async timing and cache limits

## Install
1. Install the mod and dependencies
2. Launch once to generate the config
3. Tune values to match your pack

## Modpack guidance
- Start with conservative ranges, then expand as needed
- Tune group settings to control crowd behavior in large fights
- In multiplayer, keep voice ranges reasonable to avoid constant pulls

## Support
If you report an issue, include your config and a recent log with clear reproduction steps.
