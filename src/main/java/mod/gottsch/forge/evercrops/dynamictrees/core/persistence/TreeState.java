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

import mod.gottsch.forge.evercrops.api.CatchUpState;

/**
 * Per-tree catch-up tracking state, stored once per soil-block position.
 *
 * <p>A thin extension of EverCrops' shared {@link CatchUpState} (v4 API): the two base timestamps
 * are all trees need — no light fields, since Dynamic Trees manages all light and condition checks
 * internally inside its own growth pass. Kept as a named subclass for the convenience constructor
 * and so the registry / SavedData code reads in tree terms.
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class TreeState extends CatchUpState {

    private int consecutiveDeadFertilityCount;

    public TreeState() {}

    public TreeState(long gameTime) {
        setLastCallGameTime(gameTime);
        setLastGrowthGameTime(gameTime);
    }

    @Override
    public TreeState setLastCallGameTime(long lastCallGameTime) {
        super.setLastCallGameTime(lastCallGameTime);
        return this;
    }

    @Override
    public TreeState setLastGrowthGameTime(long lastGrowthGameTime) {
        super.setLastGrowthGameTime(lastGrowthGameTime);
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
}
