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

import com.ferreusveritas.dynamictrees.block.rooty.RootyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-dimension tree state storage backed by Minecraft's SavedData system.
 * One instance is created per ServerLevel; data is saved automatically to
 * {@code <world>/data/evercrops_dynamictrees.dat} when the level saves.
 *
 * Do NOT rename DATA_NAME after the first release without providing save migration.
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class TreeSavedData extends SavedData {

    public static final String DATA_NAME = "evercrops_dynamictrees";

    private final Map<Long, TreeState> trees = new HashMap<>();

    public TreeSavedData() {}

    // -------------------------------------------------
    // Factory / lifecycle
    // -------------------------------------------------

    public static TreeSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                TreeSavedData::load,
                TreeSavedData::new,
                DATA_NAME
        );
    }

    // -------------------------------------------------
    // Serialization
    // -------------------------------------------------

    public static TreeSavedData load(CompoundTag tag) {
        TreeSavedData data = new TreeSavedData();
        ListTag list = tag.getList("trees", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long posKey = entry.getLong("pos");
            TreeState state = new TreeState();
            state.setLastCallGameTime(entry.getLong("lastCallTime"))
                 .setLastGrowthGameTime(entry.getLong("lastGrowthTime"))
                 .setConsecutiveDeadFertilityCount(entry.getInt("deadFertilityCount"));
            data.trees.put(posKey, state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, TreeState> entry : trees.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", entry.getKey());
            TreeState s = entry.getValue();
            e.putLong("lastCallTime", s.getLastCallGameTime());
            e.putLong("lastGrowthTime", s.getLastGrowthGameTime());
            e.putInt("deadFertilityCount", s.getConsecutiveDeadFertilityCount());
            list.add(e);
        }
        tag.put("trees", list);
        return tag;
    }

    // -------------------------------------------------
    // Data access
    // -------------------------------------------------

    public Optional<TreeState> get(BlockPos pos) {
        return Optional.ofNullable(trees.get(pos.asLong()));
    }

    public void put(BlockPos pos, TreeState state) {
        trees.put(pos.asLong(), state);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (trees.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    // -------------------------------------------------
    // Command / dev utilities
    // -------------------------------------------------

    /**
     * Subtracts {@code ticks} from all tracked timestamps in this dimension.
     * Kept for internal / administrative use; prefer {@link #backdateInRadius}
     * for the {@code /evercropsDT simulate} command.
     *
     * @return number of entries backdated
     */
    public int backdateAll(long ticks) {
        for (TreeState state : trees.values()) {
            state.setLastCallGameTime(state.getLastCallGameTime() - ticks);
            state.setLastGrowthGameTime(state.getLastGrowthGameTime() - ticks);
        }
        if (!trees.isEmpty()) setDirty();
        return trees.size();
    }

    /**
     * Subtracts {@code ticks} from the timestamps of every tracked tree whose
     * position is within {@code radius} blocks (Euclidean) of {@code origin}.
     * Used by {@code /evercropsDT simulate <ticks> <radius>}.
     *
     * @return number of entries backdated
     */
    public int backdateInRadius(BlockPos origin, int radius, long ticks) {
        long radiusSq = (long) radius * radius;
        int count = 0;
        for (Map.Entry<Long, TreeState> entry : trees.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.distSqr(origin) > radiusSq) continue;
            TreeState state = entry.getValue();
            state.setLastCallGameTime(state.getLastCallGameTime() - ticks);
            state.setLastGrowthGameTime(state.getLastGrowthGameTime() - ticks);
            count++;
        }
        if (count > 0) setDirty();
        return count;
    }

    /**
     * Removes entries whose SoilBlock no longer exists, but only for positions in
     * currently loaded chunks.  Unloaded entries are intentionally left untouched —
     * the SoilBlock may still be alive; we just can't confirm it without loading the
     * chunk.
     *
     * <p>Called periodically by the auto-cleanup tick handler and by the
     * {@code /evercropsDT cleanup} command.
     *
     * @param level the dimension to scan
     * @return number of stale entries removed
     */
    public int cleanupStale(ServerLevel level) {
        List<Long> toRemove = new ArrayList<>();
        for (long packedPos : trees.keySet()) {
            BlockPos pos = BlockPos.of(packedPos);
            if (!level.isLoaded(pos)) continue; // skip — can't confirm without loading chunk
            if (!(level.getBlockState(pos).getBlock() instanceof RootyBlock)) {
                toRemove.add(packedPos);
            }
        }
        toRemove.forEach(trees::remove);
        if (!toRemove.isEmpty()) setDirty();
        return toRemove.size();
    }

    /** Returns all tracked positions as packed longs (see {@link BlockPos#asLong()}). */
    public Set<Long> getKeys() {
        return Collections.unmodifiableSet(trees.keySet());
    }

    public int size() {
        return trees.size();
    }
}
