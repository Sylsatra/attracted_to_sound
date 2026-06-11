# Integration Port Design

**Goal**: Port Sound Attract mod integrations from Forge 1.20.1 to NeoForge 1.21.1 as a 1-to-1 conversion, preserving original behavior while adapting to NeoForge API changes.

**Date**: 2026-05-10

## Context

The core Sound Attract mod has been successfully ported to NeoForge 1.21.1 using a hybrid stabilization approach. The core gameplay systems (sound attraction, stealth detection, scent system, mob behavior, config, networking) are now compiling cleanly. The remaining work is to port the optional integrations that were deferred during core stabilization.

Integration jars are available in the `libs/` folder:
- `tacz-neoforge-1.21.1-1.1.8-r2.jar`
- `pointblank-neoforge-1.21.1-2.1.0.jar`
- `immersive_melodies-neoforge-0.6.2+1.21.1.jar`
- `plasmovoice-neoforge-1.21.1-2.1.9.jar`
- `voicechat-neoforge-1.21.1-2.6.17.jar`
- `enhancedai-4.1.0.0.jar`
- `SmartBrainLib-neoforge-1.21.1-1.16.11.jar`
- `spore_2.2.0f_1.21.1_neo.jar`

CS Grenades integration is excluded from this port (no jar available).

## Architecture

The integration port uses a staged, incremental approach with compile gates. Each integration is ported independently, verified to compile, then committed before moving to the next stage. This isolates failures and allows rollback to any checkpoint.

### Stages

1. **Stage 1**: Immersive Melodies + config/tag-based integrations (lowest risk)
2. **Stage 2**: Gun APIs (TACZ, PointBlank)
3. **Stage 3**: Voice (Simple Voice Chat, Plasmo Voice)
4. **Stage 4**: Spore/AI (Spore, SmartBrainLib, EnhancedAI)

### Stage 1: Immersive Melodies + Config/Tag-Based

- Port `ImmersiveMelodiesEvents` from Forge 1.20.1 to NeoForge 1.21.1
- Update server tick event registration to NeoForge patterns
- Verify NBT tag access (instrument playing state) works with 1.21 DataComponents
- Verify config caching still works
- Compile gate

### Stage 2: Gun APIs

- Port `TaczIntegration` - update event registration (`GunShootEvent`, `GunReloadEvent`) to NeoForge
- Port `PointBlankIntegration` - update event registration and attachment API
- Verify attachment API changes between Forge 1.20.1 and NeoForge 1.21.1
- Compile gate

### Stage 3: Voice

- Port Simple Voice Chat integration - update event registration and API
- Port Plasmo Voice integration - update event registration and API
- Verify voice chat API compatibility
- Compile gate

### Stage 4: Spore/AI

- Port `SporeIntegration` - update AI goal injection and event hooks
- Port `SmartBrainLibCompat` - update AI behavior hooks
- Update `EnhancedAICompat` - ensure config/tag bridge works with NeoForge
- Compile gate

## Porting Strategy Per Integration

For each integration, follow this pattern:

1. **Decompile jar** (if needed): Use a decompiler to extract class signatures and understand the 1.21.1 API surface. Output to a temporary folder for reference.

2. **Compare APIs**: Identify method signature changes between Forge 1.20.1 and NeoForge 1.21.1 versions of the target mod. Focus on:
   - Event registration patterns (Forge `@SubscribeEvent` vs NeoForge)
   - Method names and parameter types
   - Package structure changes

3. **Port integration class**: 
   - Copy the Forge 1.20.1 integration class as a starting point
   - Update imports to match NeoForge 1.21.1 packages
   - Update event registration to NeoForge patterns
   - Update method calls to match new API signatures
   - Preserve original behavior logic unchanged

4. **Update registration**: Modify `SoundAttractMod` to register the integration event handler on the NeoForge event bus when the mod is loaded.

5. **Verify compile**: Run `compileJava` and fix any errors. If API mismatches are encountered, re-examine decompiled code.

## Compile Gate Strategy

After each integration is ported, run a compile gate to verify:

1. **Run compile**: Execute `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
2. **Count errors**: `Select-String -Path .\build\compile-cmd.log -Pattern 'error:' | Measure-Object`
3. **Review errors**: If errors exist, view them and fix API mismatches
4. **If errors persist**: Decompile the jar (if not yet decompiled) and investigate the actual 1.21.1 API surface to resolve the mismatch
5. **Repeat**: Continue compile-fix loop until 0 errors
6. **Commit**: Once clean, commit with message like `"[Stage 1] Port Immersive Melodies integration to NeoForge 1.21.1"`

Decompilation is done on-demand: only when API mismatches cannot be resolved through inspection of error messages and package changes. The decompiled code is used as reference to find the correct method signatures and patterns.

## Dependency Management

The `libs/` folder already contains all integration jars. The `build.gradle` has `flatDir { dirs 'libs' }` configured, so jars are available for compilation without additional dependency declarations.

**Decompilation workflow:**
- Use a decompiler tool (e.g., CFR, Fernflower) to decompile the specific jar
- Output to a temporary folder (e.g., `decompiled/<mod-name>/`)
- Reference the decompiled code to understand API signatures and patterns
- Delete decompiled folder after the integration is successfully ported (optional cleanup)

**No changes needed to `build.gradle`:** The flatDir configuration already makes jars in `libs/` available at compile time. Integration classes can import from these jars directly.

## Testing/Verification Approach

**Verification is compile-driven**: Since this is a 1-to-1 port, the primary verification is that the code compiles cleanly. Runtime testing is deferred to after all integrations are ported.

**Per-integration verification**:
- After each integration port, run `compileJava` and verify 0 errors
- Check that integration registration code compiles without errors
- Verify imports resolve correctly (confirming API access)

**Final verification** (after all stages):
- Run full `compileJava` to verify the entire project compiles
- Run `build` to verify jar can be built successfully
- Optional: Run in a test environment with the target mods loaded to verify runtime behavior

**Behavior preservation**: The port preserves the original logic from Forge 1.20.1. No behavioral changes are made except for API adaptation (event registration, method signatures, package imports).
