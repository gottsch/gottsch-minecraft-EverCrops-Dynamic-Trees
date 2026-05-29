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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Static facade over {@link TreeSavedData}.
 *
 * SavedData is always available for a loaded ServerLevel — no explicit
 * start/stop lifecycle is needed.
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class TreeRegistry {

    private TreeRegistry() {}

    public static Optional<TreeState> get(ServerLevel level, BlockPos pos) {
        return TreeSavedData.getOrCreate(level).get(pos);
    }

    public static void put(ServerLevel level, BlockPos pos, TreeState state) {
        TreeSavedData.getOrCreate(level).put(pos, state);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        TreeSavedData.getOrCreate(level).remove(pos);
    }

    /**
     * Removes stale registry entries in loaded chunks (blocks that are no longer
     * a SoilBlock).  See {@link TreeSavedData#cleanupStale(ServerLevel)}.
     *
     * @return number of entries removed
     */
    public static int cleanup(ServerLevel level) {
        return TreeSavedData.getOrCreate(level).cleanupStale(level);
    }
}
