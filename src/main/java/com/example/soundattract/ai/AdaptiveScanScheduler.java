package com.example.soundattract.ai;

import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


public class AdaptiveScanScheduler {
    public static class CellScanTask implements Comparable<CellScanTask> {
        public final long cellKey;
        public long nextScanTick;
        public final int tier;
        public CellScanTask(long cellKey, long nextScanTick, int tier) {
            this.cellKey = cellKey;
            this.nextScanTick = nextScanTick;
            this.tier = tier;
        }
        @Override
        public int compareTo(CellScanTask other) {
            return Long.compare(this.nextScanTick, other.nextScanTick);
        }
    }

    private final PriorityQueue<CellScanTask> queue = new PriorityQueue<>();
    private final Map<Long, CellScanTask> scheduled = new HashMap<>();
    private final int baseCooldown;
    private final int[] tierShifts;

    public AdaptiveScanScheduler(int baseCooldown, int[] tierShifts) {
        this.baseCooldown = baseCooldown;
        this.tierShifts = tierShifts;
    }

    public void scheduleCell(long cellKey, int tier, long currentTick) {
        int shift = tier >= 0 && tier < tierShifts.length ? tierShifts[tier] : 0;
        long nextTick = currentTick + (baseCooldown << shift);
        CellScanTask existing = scheduled.get(cellKey);
        if (existing == null || nextTick < existing.nextScanTick) {
            CellScanTask task = new CellScanTask(cellKey, nextTick, tier);
            queue.add(task);
            scheduled.put(cellKey, task);
        }
    }

    public void tick(long currentTick, Consumer<CellScanTask> onScan) {
        while (!queue.isEmpty() && queue.peek().nextScanTick <= currentTick) {
            CellScanTask task = queue.poll();
            scheduled.remove(task.cellKey);
            onScan.accept(task);
            scheduleCell(task.cellKey, task.tier, currentTick);
        }
    }
}
