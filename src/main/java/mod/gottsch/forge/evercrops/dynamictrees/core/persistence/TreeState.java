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

import java.util.Objects;

/**
 * Per-tree catch-up tracking state. Stored once per SoilBlock position.
 *
 * No light fields — Dynamic Trees manages all light and condition checks
 * internally inside SoilBlock.updateTree().
 *
 * This is a plain POJO with no Minecraft or loader imports, keeping it
 * trivially portable across loader versions.
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class TreeState {

    private long lastCallGameTime;
    private long lastGrowthGameTime;
    private int consecutiveDeadFertilityCount;

    public TreeState() {}

    public TreeState(long gameTime) {
        this.lastCallGameTime = gameTime;
        this.lastGrowthGameTime = gameTime;
    }

    public long getLastCallGameTime() {
        return lastCallGameTime;
    }

    public TreeState setLastCallGameTime(long lastCallGameTime) {
        this.lastCallGameTime = lastCallGameTime;
        return this;
    }

    public long getLastGrowthGameTime() {
        return lastGrowthGameTime;
    }

    public TreeState setLastGrowthGameTime(long lastGrowthGameTime) {
        this.lastGrowthGameTime = lastGrowthGameTime;
        return this;
    }

    /** Consecutive randomTick checks this tree has reported fertility 0. Reset to 0 once fertility > 0. */
    public int getConsecutiveDeadFertilityCount() {
        return consecutiveDeadFertilityCount;
    }

    public TreeState setConsecutiveDeadFertilityCount(int consecutiveDeadFertilityCount) {
        this.consecutiveDeadFertilityCount = consecutiveDeadFertilityCount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TreeState treeState = (TreeState) o;
        return lastCallGameTime == treeState.lastCallGameTime
                && lastGrowthGameTime == treeState.lastGrowthGameTime
                && consecutiveDeadFertilityCount == treeState.consecutiveDeadFertilityCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastCallGameTime, lastGrowthGameTime, consecutiveDeadFertilityCount);
    }

    @Override
    public String toString() {
        return "TreeState{" +
                "lastCallGameTime=" + lastCallGameTime +
                ", lastGrowthGameTime=" + lastGrowthGameTime +
                ", consecutiveDeadFertilityCount=" + consecutiveDeadFertilityCount +
                '}';
    }
}
