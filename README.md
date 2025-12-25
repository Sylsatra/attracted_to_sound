# Attract To Sound

```
     _   _   _                  _     _____       ____                        _ 
    / \ | |_| |_ _ __ __ _  ___| |_  |_   _|__   / ___|  ___  _   _ _ __   __| |
   / _ \| __| __| '__/ _` |/ __| __|   | |/ _ \  \___ \ / _ \| | | | '_ \ / _` |
  / ___ \ |_| |_| | | (_| | (__| |_    | | (_) |  ___) | (_) | |_| | | | | (_| |
 /_/   \_\__|\__|_|  \__,_|\___|\__|   |_|\___/  |____/ \___/ \__,_|_| |_|\__,_|
                                                                                
```

Attract To Sound transforms sound into a core gameplay system.
Your actions create noise, and noise has consequences - mobs respond based on range and sound weight, making stealth critical in survival and multiplayer.

<a href="https://github.com/Admany/Quantified-API" rel="nofollow"><img src="https://i.imghippo.com/files/k1781Ug.png" alt="Quantified API Banner"></a>

## Overview
Attract To Sound builds a sound map per dimension and uses it to drive AI decisions.
The result is consistent behavior that feels close to vanilla while adding depth to movement, combat, and ambient actions.

## Core features
- ?? Sound sources with range, weight, and lifetime
- ?? Per sound overrides and data driven tags
- ?? Group behavior for more natural mob response
- ??? Voice chat support when enabled
- ?? Integration hooks for popular combat and AI mods

## Quantified API integration
Attract To Sound uses Quantified API for scheduling and cache support.
Heavy computations run off thread and results are applied on the main thread to keep Minecraft safe.
This improves tick stability without changing gameplay logic.

## Configuration
All settings live in `config/soundattract-common.toml`.
- Attracted and blacklisted entities
- Sound ranges and weights
- Stealth and movement tuning
- Voice chat thresholds and ranges
- Async timing and cache limits

## Compatibility
Attract To Sound is designed to work in large modpacks and alongside AI or combat mods.
Integrations can be toggled in config to match your pack.

## Support
If you report an issue, include your config and a recent log with clear reproduction steps.
