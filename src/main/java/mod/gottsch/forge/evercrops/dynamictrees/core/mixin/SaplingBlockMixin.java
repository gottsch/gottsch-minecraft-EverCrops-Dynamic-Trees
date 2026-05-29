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
package mod.gottsch.forge.evercrops.dynamictrees.core.mixin;

import com.ferreusveritas.dynamictrees.block.DynamicSaplingBlock;
import mod.gottsch.forge.evercrops.dynamictrees.EverCropsDT;
import mod.gottsch.forge.evercrops.dynamictrees.core.config.Config;
import mod.gottsch.forge.evercrops.dynamictrees.core.persistence.TreeCatchUp;
import mod.gottsch.forge.evercrops.dynamictrees.core.persistence.TreeRegistry;
import mod.gottsch.forge.evercrops.dynamictrees.core.persistence.TreeState;
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
 * Injects offline catch-up into Dynamic Trees' DynamicSaplingBlock.tick.
 *
 * NOTE: unlike RootyBlock (which grows on randomTick), the ferreus DynamicSaplingBlock
 * does its natural growth on the SCHEDULED tick (Block.tick), not randomTick — so this
 * mixin injects into "tick".
 *
 * DynamicSaplingBlock has no stage property — it transitions directly to a full
 * tree (plus RootyBlock) in one shot via Species.transitionToTree when
 * performBonemeal succeeds. The catch-up strategy therefore differs from the
 * RootyBlock mixin:
 *
 * - We call performBonemeal (the public "forced grow" path, same as bonemeal)
 *   rather than updateTree, bypassing the canSaplingGrowNaturally gate while
 *   still respecting canSurvive and canSaplingGrow.
 * - We loop up to min(steps, MAX_ATTEMPTS) times so that any random gate inside
 *   canSaplingGrow has multiple chances to succeed per catch-up event.
 * - When the block transitions (DynamicSaplingBlock replaced by tree structure),
 *   the sapling registry entry is removed; the new RootyBlock self-registers on
 *   its own first randomTick via RootyBlockMixin.
 *
 * @author Mark Gottschling on 2026-05-27
 */
@Mixin(DynamicSaplingBlock.class)
public class SaplingBlockMixin {

    /** Max performBonemeal attempts per catch-up event. */
    private static final int MAX_ATTEMPTS = 5;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void everCropsDT_sapling_tick(BlockState state, ServerLevel level,
                                          BlockPos pos, RandomSource rand,
                                          CallbackInfo ci) {
        if (!Config.SERVER.catchUpEnabled.get()) return;
        if (!Config.SERVER.saplingsEnabled.get()) return;

        Optional<TreeState> existing = TreeRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // First encounter: stamp timestamps now; no retroactive burst.
            TreeRegistry.put(level, pos, TreeCatchUp.createState(level));
            return; // let natural tick run
        }

        TreeState treeState = existing.get();
        int steps = TreeCatchUp.beginCatchUp(level, treeState);

        if (steps > 0) {
            EverCropsDT.LOGGER.debug("SaplingBlockMixin: catch-up {} steps at {}", steps, pos);

            DynamicSaplingBlock self = (DynamicSaplingBlock)(Object) this;
            int attempts = Math.min(steps, MAX_ATTEMPTS);
            for (int i = 0; i < attempts; i++) {
                // Re-read the current state — a previous performBonemeal call may have
                // replaced the block (canSurvive failed → dropBlock, or tree grew).
                BlockState current = level.getBlockState(pos);
                if (!(current.getBlock() instanceof DynamicSaplingBlock)) {
                    // Sapling transitioned or was dropped.  Clean up the registry entry;
                    // the new RootyBlock (if any) will self-register via RootyBlockMixin.
                    EverCropsDT.LOGGER.debug(
                            "SaplingBlockMixin: sapling gone at {} after {} attempt(s); removing entry", pos, i);
                    TreeRegistry.remove(level, pos);
                    ci.cancel();
                    return;
                }
                self.performBonemeal(level, rand, pos, current);
            }

            // All attempts made; sapling may or may not have grown.
            // Check one final time in case the last performBonemeal succeeded.
            if (!(level.getBlockState(pos).getBlock() instanceof DynamicSaplingBlock)) {
                TreeRegistry.remove(level, pos);
                ci.cancel();
                return;
            }

            // Sapling still present — save updated timestamps and suppress natural tick.
            TreeRegistry.put(level, pos, treeState);
            ci.cancel();

        } else {
            // No catch-up needed; persist refreshed timestamps and let natural tick run.
            TreeRegistry.put(level, pos, treeState);
        }
    }
}
