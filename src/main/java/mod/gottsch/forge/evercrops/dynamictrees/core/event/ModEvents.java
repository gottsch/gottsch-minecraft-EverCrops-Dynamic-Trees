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
package mod.gottsch.forge.evercrops.dynamictrees.core.event;

import com.ferreusveritas.dynamictrees.block.DynamicSaplingBlock;
import com.ferreusveritas.dynamictrees.block.rooty.RootyBlock;
import mod.gottsch.forge.evercrops.dynamictrees.EverCropsDT;
import mod.gottsch.forge.evercrops.dynamictrees.core.config.Config;
import mod.gottsch.forge.evercrops.dynamictrees.core.persistence.TreeCatchUp;
import mod.gottsch.forge.evercrops.dynamictrees.core.persistence.TreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Game-bus event listeners for EverCrops: Dynamic Trees.
 *
 * Registers a TreeState entry when a SoilBlock is placed by an entity, and
 * removes the entry when a SoilBlock is broken. This covers direct placement
 * scenarios; the primary registration path for tree-grown SoilBlocks is the
 * SoilBlockMixin HEAD inject (which creates a state on first randomTick if
 * none exists).
 *
 * @author Mark Gottschling on 2026-05-27
 */
@Mod.EventBusSubscriber(modid = EverCropsDT.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        TreeRegistry.put(serverLevel, pos, TreeCatchUp.createState(serverLevel));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        if (isTracked(state)) {
            TreeRegistry.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }

    /**
     * Periodic automatic cleanup of stale registry entries.
     *
     * Fires every game tick per loaded dimension, but the body is gated behind
     * a modulo check so actual work runs only every {@code autoCleanupIntervalTicks}
     * ticks (default 36 000 = 30 minutes).  Only ServerLevel dimensions are scanned;
     * client-side levels and the check itself are skipped cheaply.
     *
     * Only positions in currently loaded chunks are inspected — unloaded entries
     * are left untouched because we cannot read their block state without forcing
     * chunk loads.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!Config.SERVER.autoCleanupEnabled.get()) return;

        int interval = Config.SERVER.autoCleanupIntervalTicks.get();
        if (serverLevel.getGameTime() % interval != 0) return;

        int removed = TreeRegistry.cleanup(serverLevel);
        if (removed > 0) {
            EverCropsDT.LOGGER.info("Auto-cleanup removed {} stale tree entr{} in {}.",
                    removed, removed == 1 ? "y" : "ies",
                    serverLevel.dimension().location());
        } else {
            EverCropsDT.LOGGER.debug("Auto-cleanup: no stale entries in {}.",
                    serverLevel.dimension().location());
        }
    }

    /**
     * Returns true if this block state should be tracked for catch-up growth.
     * Covers both DynamicSaplingBlock (pre-tree) and SoilBlock (established tree).
     */
    private static boolean isTracked(BlockState state) {
        return state.getBlock() instanceof RootyBlock
            || state.getBlock() instanceof DynamicSaplingBlock;
    }
}
