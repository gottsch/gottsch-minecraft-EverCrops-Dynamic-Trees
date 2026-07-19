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
package mod.gottsch.neo.evercrops.dynamictrees.core.mixin;

import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.dtteam.dynamictrees.tree.ChunkTreeHelper;
import mod.gottsch.neo.evercrops.dynamictrees.EverCropsDT;
import mod.gottsch.neo.evercrops.dynamictrees.core.config.Config;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeCatchUp;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeRegistry;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Injects offline catch-up growth into Dynamic Trees' SoilBlock.randomTick.
 *
 * Algorithm (single HEAD inject; no RETURN inject needed):
 *
 * 1. First encounter (no TreeState): stamp both timestamps to now and let the
 *    natural randomTick run.  Trees that pre-date mod installation are handled
 *    gracefully — they receive no retroactive catch-up burst.
 *
 * 2. Loaded-chunk tick (callDelta ≤ 2 × AVG_CALL_TICK_INTERVAL): TreeCatchUp
 *    refreshes BOTH timestamps to 'now' so growthDelta stays calibrated. No
 *    catch-up is applied; natural tick runs.
 *
 * 3. Post-reload with insufficient growth time (growthDelta ≤ 2 × avgGrowthInterval):
 *    No catch-up; natural tick runs.
 *
 * 4. Post-reload with enough elapsed time: compute missed growth steps, loop
 *    SoilBlock.updateTree(…, natural=false) N times (false suppresses disease
 *    checks and voluntary drops that are inappropriate for simulated catch-up),
 *    then cancel the natural tick so we don't apply N+1 steps.
 *
 * No light gating — Dynamic Trees manages all light and condition checks
 * internally inside SoilBlock.updateTree() → Species.update().
 *
 * @author Mark Gottschling on 2026-05-27
 */
@Mixin(SoilBlock.class)
public class SoilBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void everCropsDT_randomTick(BlockState state, ServerLevel level,
                                        BlockPos pos, RandomSource rand,
                                        CallbackInfo ci) {
        if (!Config.SERVER.catchUpEnabled.get()) return;

        // DT's SoilBlock.updateTree silently no-ops unless the soil's chunk AND all
        // eight neighbours are entity-ticking (ChunkTreeHelper.isSurroundedByLoadedChunks).
        // This is commonly false in the first ticks after a chunk reloads — exactly when
        // our mixin fires. If we delivered catch-up steps now they would be consumed for
        // zero growth and the elapsed time would be lost. Defer instead: let the natural
        // tick run (which DT also no-ops) and don't touch the timestamps. beginCatchUp
        // will compute the full elapsed delta on a later tick once the chunks are live.
        if (!ChunkTreeHelper.isSurroundedByLoadedChunks(level, pos)) {
            EverCropsDT.LOGGER.debug("SoilBlockMixin: chunks not fully loaded at {}; deferring catch-up", pos);
            return;
        }

        Optional<TreeState> existing = TreeRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // First encounter: stamp timestamps now; no retroactive catch-up.
            TreeRegistry.put(level, pos, TreeCatchUp.createState(level));
            return; // let natural tick run
        }

        TreeState treeState = existing.get();

        // Fertility 0 means DT will not grow this tree at all (Species.grow bails out
        // immediately), so continuing to track it — and computing catch-up math for it on
        // every randomTick forever — is wasted work. This is the main registry-bloat cost for
        // the large numbers of untouched wild/naturally-generated trees a player flies past.
        // Only drop the entry after several consecutive dead checks, in case fertility is
        // fluctuating right at the boundary. If fertility later recovers, the tree is
        // re-registered automatically on its next randomTick (first-encounter path above).
        int fertility = state.getValue(SoilBlock.FERTILITY);
        if (fertility == 0) {
            int deadCount = treeState.getConsecutiveDeadFertilityCount() + 1;
            if (deadCount >= Config.SERVER.deadFertilityCleanupThreshold.get()) {
                EverCropsDT.LOGGER.debug(
                        "SoilBlockMixin: fertility 0 for {} consecutive checks at {}; removing entry",
                        deadCount, pos);
                TreeRegistry.remove(level, pos);
                return; // let natural tick run; nothing left to catch up
            }
            treeState.setConsecutiveDeadFertilityCount(deadCount);
        } else if (treeState.getConsecutiveDeadFertilityCount() != 0) {
            treeState.setConsecutiveDeadFertilityCount(0);
        }

        int steps = TreeCatchUp.beginCatchUp(level, treeState);

        if (steps > 0) {
            EverCropsDT.LOGGER.debug("SoilBlockMixin: catch-up {} steps at {}", steps, pos);

            SoilBlock self = (SoilBlock)(Object) this;
            for (int i = 0; i < steps; i++) {
                // Re-read state each iteration — updateTree may transition the block.
                BlockState current = level.getBlockState(pos);
                if (!(current.getBlock() instanceof SoilBlock)) {
                    // Soil block was replaced during catch-up (e.g. tree fully grown and
                    // soil decayed); stop and clean up the registry entry.
                    EverCropsDT.LOGGER.debug("SoilBlockMixin: soil replaced at {} after {} steps; removing entry", pos, i);
                    TreeRegistry.remove(level, pos);
                    ci.cancel();
                    return;
                }
                self.updateTree(current, level, pos, rand, false);
            }

            // Persist the updated timestamps from beginCatchUp.
            TreeRegistry.put(level, pos, treeState);
            // Suppress the natural tick — catch-up already covers this tick's growth.
            ci.cancel();

        } else {
            // No catch-up needed (chunk was loaded, or growthDelta below threshold).
            // Persist the refreshed timestamps from beginCatchUp.
            TreeRegistry.put(level, pos, treeState);
            // Let natural tick run normally.
        }
    }
}
