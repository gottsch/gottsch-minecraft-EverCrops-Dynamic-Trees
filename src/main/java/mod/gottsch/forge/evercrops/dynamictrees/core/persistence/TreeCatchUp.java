/*
 * This file is part of EverCrops: Dynamic Trees.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * EverCrops: Dynamic Trees is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverCrops: Dynamic Trees is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverCrops: Dynamic Trees.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.evercrops.dynamictrees.core.persistence;

import mod.gottsch.forge.evercrops.dynamictrees.core.config.Config;
import net.minecraft.server.level.ServerLevel;

/**
 * Shared catch-up timing logic for Dynamic Trees soil blocks.
 *
 * Two-threshold algorithm (mirrors EverCrops CropCatchUp):
 *   - If the call delta is below 2× AVG_CALL_TICK_INTERVAL, the chunk was never
 *     really unloaded — refresh BOTH timestamps so that growthDelta stays calibrated
 *     with the loaded tick rate and never grows stale.
 *   - If the growth delta is below 2× AVG_GROWTH_TICK_INTERVAL, not enough time
 *     has passed since the last growth — no catch-up needed yet.
 *   - Otherwise, compute how many growth steps were missed.
 *
 * No light gating — Dynamic Trees manages all light and condition checks
 * internally inside SoilBlock.updateTree().
 *
 * AVG_GROWTH_TICK_INTERVAL is read from config on every call so that server
 * admins can adjust it without restarting. It should be tuned to match DT's
 * treeGrowthMultiplier:  interval ≈ 1365 / min(multiplier, 1.0).
 *
 * @author Mark Gottschling on 2026-05-27
 */
public final class TreeCatchUp {

    /** Average game ticks between randomTick calls on any single block. */
    public static final int AVG_CALL_TICK_INTERVAL = 1350;

    private TreeCatchUp() {}

    /**
     * Decide whether catch-up growth should fire on this random tick, and if so
     * how many growth steps to apply. Updates the timing fields on {@code treeState}
     * as a side effect; the caller is responsible for performing the actual
     * block updates and persisting the state via {@link TreeRegistry#put}.
     *
     * The raw quotient is uncapped — a tree left untouched for a very long time can
     * accumulate a huge step count, and applying all of it synchronously in one tick
     * risks a lag spike (worse when several stale trees load at once, e.g. flying
     * fast through terrain). {@code maxCatchUpStepsPerEvent} bounds the work done in
     * a single call; any remainder is pushed back onto {@code lastGrowthGameTime} so
     * it is picked up on a later randomTick instead of being lost.
     *
     * @param level     the server level (used for current game time only)
     * @param treeState tracked state for this soil block position (mutated in place)
     * @return number of growth steps to apply this call (0 if no catch-up this tick)
     */
    public static int beginCatchUp(ServerLevel level, TreeState treeState) {
        long now = level.getGameTime();
        int avgGrowthInterval = Config.SERVER.avgGrowthTickInterval.get();

        long callDelta = now - treeState.getLastCallGameTime();
        if (callDelta <= AVG_CALL_TICK_INTERVAL * 2L) {
            // Chunk was never really unloaded; keep BOTH timestamps current so that
            // growthDelta correctly reflects only the actual offline period on the next
            // reload, rather than also accumulating loaded idle time.
            treeState.setLastCallGameTime(now)
                     .setLastGrowthGameTime(now);
            return 0;
        }

        long growthDelta = now - treeState.getLastGrowthGameTime();
        if (growthDelta <= avgGrowthInterval * 2L) {
            // Not enough time has elapsed since last growth; no catch-up yet.
            // Leave timestamps untouched (mirrors EverCrops between-threshold behaviour).
            return 0;
        }

        int quotient = (int) Math.floor((double) growthDelta / avgGrowthInterval);
        long remainder = growthDelta % avgGrowthInterval;
        treeState.setLastGrowthGameTime(now - remainder)
                 .setLastCallGameTime(now);

        int maxSteps = Config.SERVER.maxCatchUpStepsPerEvent.get();
        if (quotient > maxSteps) {
            int deferredSteps = quotient - maxSteps;
            treeState.setLastGrowthGameTime(
                    treeState.getLastGrowthGameTime() - (long) deferredSteps * avgGrowthInterval);
            quotient = maxSteps;
        }
        return quotient;
    }

    /**
     * Build a fresh {@link TreeState} stamped with the current game time.
     * Called on first randomTick for trees that pre-date mod installation,
     * and from ModEvents on placement.
     */
    public static TreeState createState(ServerLevel level) {
        return new TreeState(level.getGameTime());
    }
}
