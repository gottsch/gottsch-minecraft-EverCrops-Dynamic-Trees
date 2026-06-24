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

import mod.gottsch.forge.evercrops.api.CatchUpDecision;
import mod.gottsch.forge.evercrops.dynamictrees.core.config.Config;
import net.minecraft.server.level.ServerLevel;

/**
 * Dynamic-Trees-facing facade over EverCrops' shared catch-up engine.
 *
 * The two-threshold timing algorithm now lives once in
 * {@link mod.gottsch.forge.evercrops.api.CatchUpDecision#computeStepsUnlit} (v4 API); this class
 * just supplies the tree-specific inputs: DT's per-call interval and the configured growth
 * interval. No light gating — Dynamic Trees manages all light and condition checks internally
 * inside its own growth pass.
 *
 * The growth interval is read from config on every call so that server admins can adjust it
 * without restarting. It should be tuned to match DT's treeGrowthMultiplier:
 * interval ≈ 1365 / min(multiplier, 1.0).
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
     * @param level     the server level (used for current game time only)
     * @param treeState tracked state for this soil block position (mutated in place)
     * @return number of growth steps to apply (0 if no catch-up this tick)
     */
    public static int beginCatchUp(ServerLevel level, TreeState treeState) {
        return CatchUpDecision.computeStepsUnlit(treeState, level.getGameTime(),
                AVG_CALL_TICK_INTERVAL, Config.SERVER.avgGrowthTickInterval.get());
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
