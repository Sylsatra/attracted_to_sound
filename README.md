# Sound Attract

Make mobs react to sound in a way that feels fair, configurable, and intense. Sound Attract turns movement, blocks, combat, and voice into real signals that mobs can track, so stealth and noise matter again.

<a href="https://github.com/Admany/Quantified-API" rel="nofollow"><img src="https://i.imghippo.com/files/k1781Ug.png" alt="Quantified API Banner"></a>

## Why it is different
- Mobs respond to a real sound map rather than a simple radius check
- Sounds have weight, range, and lifetime so noisy actions carry risk
- Strong config control so modpacks can tune balance without code changes
- Performance focused with Quantified API scheduling and cache support

## Core features
- Movement, combat, block interactions, and explosions create attractors
- Per sound tuning with overrides and data driven tags
- Group behavior and edge mob logic for more believable reactions
- Voice chat support so real speech can trigger attention
- Integration hooks for popular combat and AI mods

## Quantified API integration
Sound Attract uses the Quantified API for async scheduling and caching to keep ticks smooth under load. Heavy computations run off thread, then results are applied on the main thread to keep Minecraft safe. This keeps behavior consistent while improving performance and stability.

## Configuration
Everything is configurable in `config/soundattract-common.toml`. Highlights include
- Attracted and blacklisted mobs
- Sound ranges and weights
- Stealth and movement tuning
- Voice chat ranges and thresholds
- Async timing and cache limits

## Getting started
1. Install the mod and its dependencies
2. Launch once to generate the config
3. Tune the settings to match your pack

## Modpack tips
- Start with conservative ranges, then expand once you see how mobs react
- Tune group settings to control crowd behavior in large fights
- For multiplayer, keep voice ranges reasonable to avoid constant pulls

## Support
If you hit issues, include your config and a recent log in your report. Clear repro steps help a lot.
