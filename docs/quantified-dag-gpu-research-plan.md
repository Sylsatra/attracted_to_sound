# Quantified API DAG and GPU Acceleration Research Plan

## Overview
Research whether Sound Attract mod should adopt Quantified API 2.0 DAG (task graphs) and GPU acceleration features.

## Current Quantified API Usage

### 1. QuantifiedCacheCompat
- Uses `getCached()` for LOS raycast result caching
- Cache bucket: "soundattract_los_raycast"
- TTL-based caching with memory pressure management

### 2. QuantifiedWorkScheduler
- Uses `trySubmitTask()` for group compute (mob grouping computations)
- Uses `trySubmitTask()` for sound score computations
- Uses `ParallelComputeBridge` for parallel sound score processing
- Fallback to LocalWorkScheduler when Quantified unavailable

### 3. AsyncManager
- Uses `trySubmitTask()` for async task execution

## DAG (Task Graphs) Evaluation

### What DAG Provides
- Express task dependencies explicitly (A → B → C)
- Automatic topological order execution
- Seed nodes run immediately
- Transform nodes read dependency outputs via `ctx.result(handle)`
- Join multiple branches with `dependsOn()`
- Concurrent execution of independent nodes

### Current Task Patterns
- Group compute: Independent computation, no dependencies
- Sound score: Independent computations per sound, already uses parallel
- LOS raycast: Independent per ray, already cached

### DAG Suitability Assessment
**Recommendation: NOT SUITABLE**

**Rationale:**
- Current tasks are independent parallel computations, not dependency chains
- No A → B → C patterns that would benefit from explicit DAG
- Parallel compute already handles concurrent processing efficiently
- Adding DAG would add complexity without performance benefit
- If future features introduce chained computations (e.g., preprocess → compute → postprocess), then DAG would be valuable

## GPU Acceleration Evaluation

### What GPU Provides
- Vulkan (preferred) or OpenCL (fallback) backend
- Router checks: preferVulkan, preferOpenCl, requireVulkan, requireOpenCl, preferGpu, cpuOnly
- Automatic fallback to CPU if GPU fails
- Requires GLSL compute shaders compiled to SPIR-V
- ComputeRequest can route to GPU if task seems fit

### Potential GPU Candidates

#### 1. Sound Score Computation
**Workload:** Mathematical scoring calculations for sound candidates
- Distance calculations
- Weight/muffling factor computations
- Score aggregation

**GPU Suitability:** MODERATE
- Parallelizable across sound candidates
- Mathematical operations benefit from GPU
- Already uses parallel compute (CPU)
- **Challenge:** Requires writing GLSL compute shaders
- **Benefit:** Depends on batch size (larger batches = better GPU utilization)

#### 2. LOS Raycast (DDA Traversal)
**Workload:** Geometric line-of-sight calculations
- Voxel traversal (Amanatides-Woo algorithm)
- AABB intersection tests
- Collision shape checks

**GPU Suitability:** LOW
- Branch-heavy algorithm (conditional voxel steps)
- Requires block state access (world data)
- Already optimized with DDA and caching
- **Challenge:** Significant shader complexity, world data access patterns
- **Benefit:** Limited due to branch divergence and data access patterns

#### 3. Muffling Calculations
**Workload:** Ray traversal for sound muffling
- DDA traversal through blocks
- Block type-based muffling factors

**GPU Suitability:** LOW
- Similar to LOS raycast (branch-heavy)
- Block state access required
- Already optimized with DDA
- **Challenge:** Shader complexity, world data access
- **Benefit:** Limited

### GPU Recommendation
**Recommendation: DEFER - EVALUATE SOUND SCORE ONLY**

**Rationale:**
- Sound score computation is the most GPU-friendly workload
- Pure mathematical operations, minimal branching
- Already parallelized on CPU
- **Prerequisites:**
  - Write GLSL compute shader for sound scoring
  - Benchmark CPU vs GPU performance with realistic batch sizes
  - Ensure CPU fallback works correctly
  - Test with various batch sizes to find breakeven point

**Other workloads (LOS, muffling):**
- Not suitable due to branch-heavy algorithms and world data access
- CPU implementation is already well-optimized
- GPU would require significant shader complexity with limited benefit

## Research Steps

### Phase 1: Sound Score GPU Feasibility (HIGH PRIORITY)
1. **Analyze Sound Score Algorithm**
   - Profile current CPU implementation
   - Identify parallelizable components
   - Measure computation time per sound candidate

2. **Design GLSL Compute Shader**
   - Implement sound score calculation in GLSL
   - Handle candidate data structures in GPU memory
   - Implement SPIR-V compilation pipeline

3. **Benchmark Comparison**
   - Test GPU implementation with various batch sizes (10, 50, 100, 500 candidates)
   - Compare against current CPU parallel implementation
   - Identify breakeven point where GPU becomes faster

4. **Integration Planning**
   - Add GPU preference option to QuantifiedWorkScheduler
   - Implement CPU fallback path
   - Add configuration option for GPU enable/disable

### Phase 2: DAG Evaluation (LOW PRIORITY)
1. **Identify Future Dependency Opportunities**
   - Review planned features for chained computations
   - Document where DAG would add value
   - Keep as future consideration if patterns emerge

### Phase 3: LOS/Muffling GPU Evaluation (LOW PRIORITY)
1. **Algorithm Analysis**
   - Evaluate if DDA can be GPU-accelerated
   - Research world data access patterns for GPU
   - Assess branch divergence impact

2. **Feasibility Assessment**
   - Determine if shader complexity is justified
   - Benchmark if implementation is pursued

## Expected Outcomes

### Sound Score GPU
- **Best Case:** 2-5x speedup for large batches (>100 candidates)
- **Worst Case:** No improvement or slower due to overhead
- **Decision Point:** If GPU shows >1.5x speedup for realistic batch sizes, implement

### DAG
- **Current:** No benefit, not recommended
- **Future:** Re-evaluate if dependency chains are introduced

### LOS/Muffling GPU
- **Expected:** Limited benefit due to algorithm characteristics
- **Recommendation:** Do not pursue unless significant algorithm redesign

## Timeline Estimate
- Phase 1 (Sound Score GPU): 1-2 weeks (including benchmarking)
- Phase 2 (DAG): 1 day (documentation only)
- Phase 3 (LOS/Muffling): 1 week (if pursued, but not recommended)

## Risk Assessment
- **GPU Shader Complexity:** High risk of bugs, requires GLSL expertise
- **Platform Compatibility:** Vulkan/OpenCL availability varies
- **Performance Uncertainty:** May not beat well-optimized CPU code
- **Maintenance Burden:** Additional code path to maintain and test

## Conclusion
**Recommendation:** Focus research on sound score GPU acceleration only. Defer DAG until dependency patterns emerge. Do not pursue LOS/muffling GPU due to algorithm characteristics.
