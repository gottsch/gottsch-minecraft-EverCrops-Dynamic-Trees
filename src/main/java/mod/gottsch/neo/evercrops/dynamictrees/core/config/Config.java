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
package mod.gottsch.neo.evercrops.dynamictrees.core.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side configuration for EverCrops: Dynamic Trees.
 *
 * avgGrowthTickInterval should be tuned to match DT's treeGrowthMultiplier setting:
 *   interval ≈ 1365 / min(multiplier, 1.0)
 * Under the default multiplier of 1.0 a growth signal fires every randomTick call
 * (~1365 ticks on average), so the default of 1500 is a conservative safe margin.
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class Config {

    public static final ModConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    static {
        final Pair<ServerConfig, ModConfigSpec> serverPair =
                new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER      = serverPair.getLeft();
    }

    private Config() {}

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    // -----------------------------------------------------------------------

    public static class ServerConfig {

        public final ModConfigSpec.BooleanValue catchUpEnabled;
        public final ModConfigSpec.IntValue     avgGrowthTickInterval;
        public final ModConfigSpec.IntValue     maxCatchUpStepsPerEvent;
        public final ModConfigSpec.BooleanValue saplingsEnabled;
        public final ModConfigSpec.BooleanValue autoCleanupEnabled;
        public final ModConfigSpec.IntValue     autoCleanupIntervalTicks;
        public final ModConfigSpec.IntValue     deadFertilityCleanupThreshold;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment("EverCrops: Dynamic Trees catch-up settings").push("trees");

            catchUpEnabled = builder
                    .comment("Enable offline catch-up growth for Dynamic Trees trees. " +
                             "When a chunk reloads after being unloaded, each tracked tree " +
                             "receives the growth steps it would have received had the chunk " +
                             "remained loaded.")
                    .define("catchUpEnabled", true);

            avgGrowthTickInterval = builder
                    .comment("Average game ticks between successful growth signals for a single tree. " +
                             "Under DT's default treeGrowthMultiplier=1.0, growth fires every randomTick " +
                             "call (~1365 ticks on average), so the default of 1500 is a safe margin. " +
                             "Formula: ~1365 / min(treeGrowthMultiplier, 1.0). " +
                             "Increase this value if trees grow too fast during catch-up; decrease " +
                             "to make catch-up more aggressive. Also used as the sapling growth interval.")
                    .defineInRange("avgGrowthTickInterval", 1500, 100, 1_000_000);

            maxCatchUpStepsPerEvent = builder
                    .comment("Maximum number of growth steps applied to a single tree in one catch-up " +
                             "event. A tree left untouched for a very long time can accumulate a huge " +
                             "step count; applying them all synchronously in one tick can cause a lag " +
                             "spike (especially when several stale trees load at once, e.g. flying fast " +
                             "through terrain). Any steps beyond this cap are deferred and applied on a " +
                             "later randomTick instead of being lost.")
                    .defineInRange("maxCatchUpStepsPerEvent", 100, 1, 100_000);

            saplingsEnabled = builder
                    .comment("Enable offline catch-up for DynamicSaplingBlocks. " +
                             "When a chunk reloads after being unloaded, each tracked sapling receives " +
                             "up to 5 transition attempts (equivalent to bonemeal) per missed growth " +
                             "interval. Uses the same avgGrowthTickInterval as tree growth. " +
                             "Has no effect if catchUpEnabled is false.")
                    .define("saplingsEnabled", true);

            builder.pop();
            builder.comment("EverCrops: Dynamic Trees registry cleanup settings").push("cleanup");

            autoCleanupEnabled = builder
                    .comment("Periodically scan the tree registry and remove entries whose SoilBlock " +
                             "no longer exists (e.g. trees killed by DT disease/decay, which bypass " +
                             "BreakEvent). Only loaded chunks are checked; unloaded entries are left " +
                             "untouched.")
                    .define("autoCleanupEnabled", true);

            autoCleanupIntervalTicks = builder
                    .comment("How often (in game ticks) the automatic cleanup scan runs per dimension. " +
                             "1200 = 1 minute, 36000 = 30 minutes, 72000 = 60 minutes. " +
                             "The scan only touches loaded chunks, so it is cheap at typical values.")
                    .defineInRange("autoCleanupIntervalTicks", 36_000, 1_200, 288_000);

            deadFertilityCleanupThreshold = builder
                    .comment("Number of consecutive randomTick checks a tracked tree may report " +
                             "fertility 0 before its registry entry is removed. Fertility 0 means DT " +
                             "will not grow the tree at all, so continuing to track it (and compute " +
                             "catch-up math for it on every randomTick) is wasted work — this is the " +
                             "main cost for large numbers of untouched wild/naturally-generated trees. " +
                             "If fertility later recovers (e.g. bonemeal), the tree is re-registered " +
                             "automatically on its next randomTick, same as a tree the mod has never " +
                             "seen before.")
                    .defineInRange("deadFertilityCleanupThreshold", 3, 1, 100);

            builder.pop();
        }
    }
}
