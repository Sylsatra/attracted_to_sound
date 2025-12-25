# Changelog

## 4.3.0-1.20.1 Quantified integration update
- Added Quantified API as a compile time dependency and register on startup
- Switched cache integration to direct Quantified cache manager with memory pressure handling
- Routed async group and sound scoring tasks through Quantified task scheduler with timeouts
- Added a Quantified backed async manager with a main thread handoff queue for thread safe work
- Rewired worker computations to use the Quantified async manager instead of a local thread pool
- Reduced per tick allocations in sound tracking and reused dedupe sets for lower GC pressure
- Snapshot sound candidates under lock and perform heavier scoring work after releasing it to reduce contention
- Async sound scoring now uses muffled range and weight from main thread snapshots for accurate off thread selection
- Improved logging bridge so Quantified logs flow into the mod logger
- Preserved safe fallbacks when Quantified is not present
