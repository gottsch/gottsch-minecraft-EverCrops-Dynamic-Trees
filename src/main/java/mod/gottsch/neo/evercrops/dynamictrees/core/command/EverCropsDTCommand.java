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
package mod.gottsch.neo.evercrops.dynamictrees.core.command;

import com.dtteam.dynamictrees.block.sapling.DynamicSaplingBlock;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import mod.gottsch.neo.evercrops.dynamictrees.core.config.Config;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeCatchUp;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeRegistry;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeSavedData;
import mod.gottsch.neo.evercrops.dynamictrees.core.persistence.TreeState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Debug/testing commands for EverCrops: Dynamic Trees.
 *
 * /evercropsDT register &lt;radius&gt;    – scan all loaded blocks within &lt;radius&gt; of the
 *                                        player for unregistered SoilBlocks and stamp a
 *                                        fresh TreeState for each one.  Useful for testing
 *                                        (DT plants SoilBlocks programmatically, so they
 *                                        normally register on their first randomTick).
 *
 * /evercropsDT simulate &lt;ticks&gt; &lt;radius&gt; – backdate every tracked tree within
 *                                        &lt;radius&gt; blocks by &lt;ticks&gt; game ticks; the
 *                                        next randomTick on each SoilBlock will enter
 *                                        the catch-up growth path.
 *
 * /evercropsDT tick &lt;radius&gt;        – force a randomTick on every tracked SoilBlock
 *                                        within &lt;radius&gt; blocks of the player; combine
 *                                        with simulate for instant results.
 *
 * /evercropsDT cleanup               – remove registry entries for SoilBlocks that no
 *                                        longer exist in loaded chunks (e.g. trees killed
 *                                        by DT disease/decay).
 *
 * /evercropsDT inspect               – show the stored TreeState for the block at the
 * /evercropsDT inspect &lt;x&gt; &lt;y&gt; &lt;z&gt;   player's feet (or at given coordinates).
 *
 * @author Mark Gottschling on 2026-05-27
 */
public class EverCropsDTCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("evercropsDT")
                .requires(source -> source.hasPermission(2))

                // /evercropsDT register <radius>
                .then(Commands.literal("register")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> register(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))

                // /evercropsDT simulate <ticks> <radius>
                .then(Commands.literal("simulate")
                    .then(Commands.argument("ticks", LongArgumentType.longArg(1))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                            .executes(ctx -> simulate(
                                    ctx.getSource(),
                                    LongArgumentType.getLong(ctx, "ticks"),
                                    IntegerArgumentType.getInteger(ctx, "radius"))))))

                // /evercropsDT tick <radius>
                .then(Commands.literal("tick")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> tick(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))

                // /evercropsDT cleanup
                .then(Commands.literal("cleanup")
                    .executes(ctx -> cleanup(ctx.getSource())))

                // /evercropsDT inspect  /  /evercropsDT inspect <x> <y> <z>
                .then(Commands.literal("inspect")
                    .executes(ctx -> inspect(ctx.getSource(), null))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> inspect(
                                ctx.getSource(),
                                BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
        );
    }

    // ------------------------------------------------------------------
    // /evercropsDT register <radius>
    // ------------------------------------------------------------------

    /**
     * Scans every block in a cube of side (2*radius+1) centred on the player,
     * registers any SoilBlock that is not already tracked, and reports the count.
     *
     * DT places SoilBlocks programmatically (not via EntityPlaceEvent), so they
     * normally enter the registry on their first randomTick.  This command forces
     * immediate registration so that simulate + tick can be used straight away
     * without waiting for a random tick.
     */
    private static int register(CommandSourceStack source, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        int registeredTrees    = 0;
        int registeredSaplings = 0;
        int alreadyTracked     = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState blockState = level.getBlockState(pos);

                    if (blockState.getBlock() instanceof SoilBlock) {
                        if (TreeRegistry.get(level, pos).isPresent()) {
                            alreadyTracked++;
                        } else {
                            TreeRegistry.put(level, pos, TreeCatchUp.createState(level));
                            registeredTrees++;
                        }

                    } else if (blockState.getBlock() instanceof DynamicSaplingBlock) {
                        if (TreeRegistry.get(level, pos).isPresent()) {
                            alreadyTracked++;
                        } else {
                            // Stamp the sapling and immediately give it a randomTick so it
                            // can attempt to transition to a tree right now.  The mixin HEAD
                            // will see the fresh state (callDelta ≈ 0) and skip catch-up,
                            // then the natural tick path fires performBonemeal via randomTick.
                            TreeRegistry.put(level, pos, TreeCatchUp.createState(level));
                            blockState.randomTick(level, pos, level.getRandom());
                            registeredSaplings++;
                        }
                    }
                }
            }
        }

        final int trees    = registeredTrees;
        final int saplings = registeredSaplings;
        final int existing = alreadyTracked;
        source.sendSuccess(() -> Component.literal(
                String.format("Registered %d tree(s) and %d sapling(s) within %d blocks " +
                              "(%d already tracked). " +
                              "Use '/evercropsDT simulate <ticks> %d' then '/evercropsDT tick %d' to test catch-up.",
                        trees, saplings, radius, existing, radius, radius))
                .withStyle((trees + saplings) > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return registeredTrees + registeredSaplings;
    }

    // ------------------------------------------------------------------
    // /evercropsDT simulate <ticks> <radius>
    // ------------------------------------------------------------------

    private static int simulate(CommandSourceStack source, long ticks, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        TreeSavedData data = TreeSavedData.getOrCreate(level);
        int count = data.backdateInRadius(origin, radius, ticks);

        double minutes = ticks / 1200.0;
        source.sendSuccess(() -> Component.literal(
                String.format("Backdated %d tree(s) by %d ticks (%.1f min) within %d blocks. " +
                              "Use '/evercropsDT tick %d' to apply growth immediately.",
                        count, ticks, minutes, radius, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return count;
    }

    // ------------------------------------------------------------------
    // /evercropsDT tick <radius>
    // ------------------------------------------------------------------

    private static int tick(CommandSourceStack source, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        TreeSavedData data = TreeSavedData.getOrCreate(level);
        long radiusSq = (long) radius * radius;
        int triggered = 0;

        List<Long> keySnapshot = new ArrayList<>(data.getKeys());
        for (long packedPos : keySnapshot) {
            BlockPos pos = BlockPos.of(packedPos);
            if (pos.distSqr(origin) <= radiusSq) {
                BlockState blockState = level.getBlockState(pos);
                if (blockState.isRandomlyTicking()) {
                    blockState.randomTick(level, pos, level.getRandom());
                    triggered++;
                }
            }
        }

        final int result = triggered;
        source.sendSuccess(() -> Component.literal(
                String.format("Triggered randomTick for %d tree blocks within %d blocks of you.",
                        result, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return triggered;
    }

    // ------------------------------------------------------------------
    // /evercropsDT cleanup
    // ------------------------------------------------------------------

    private static int cleanup(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int removed = TreeRegistry.cleanup(level);

        if (removed > 0) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Removed %d stale tree entr%s from %s " +
                                  "(SoilBlock no longer present in loaded chunks).",
                            removed, removed == 1 ? "y" : "ies",
                            level.dimension().location()))
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "No stale entries found in loaded chunks of " + level.dimension().location() + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // /evercropsDT inspect [x y z]
    // ------------------------------------------------------------------

    private static int inspect(CommandSourceStack source, BlockPos targetPos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();

        BlockPos pos = targetPos != null
                ? targetPos
                : source.getPlayerOrException().blockPosition();

        TreeSavedData data = TreeSavedData.getOrCreate(level);
        Optional<TreeState> opt = data.get(pos);

        if (opt.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No TreeState recorded at " + pos.toShortString()
                    + " in " + level.dimension().location())
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        TreeState state = opt.get();
        long now = level.getGameTime();
        int avgGrowthInterval = Config.SERVER.avgGrowthTickInterval.get();

        long callDelta   = now - state.getLastCallGameTime();
        long growthDelta = now - state.getLastGrowthGameTime();

        boolean wouldTrigger = callDelta   > TreeCatchUp.AVG_CALL_TICK_INTERVAL * 2L
                            && growthDelta > avgGrowthInterval * 2L;

        int catchUpSteps = wouldTrigger
                ? (int) Math.floor((double) growthDelta / avgGrowthInterval)
                : 0;

        source.sendSuccess(() -> Component.literal(
                "=== EverCrops: DT inspect @ " + pos.toShortString() + " ===")
                .withStyle(ChatFormatting.AQUA), false);

        // Block-level diagnostics: what's actually at this position, and -- if it's
        // a SoilBlock -- its FERTILITY value.  This is the most common reason a
        // tree refuses to grow during catch-up: DT's Species.grow() short-circuits
        // when fertility == 0, so every catch-up step we deliver is a free no-op.
        BlockState blockState = level.getBlockState(pos);
        final String blockDesc;
        final ChatFormatting blockColor;
        final Integer fertility;
        if (blockState.getBlock() instanceof SoilBlock) {
            int f = blockState.getValue(SoilBlock.FERTILITY);
            fertility = f;
            blockDesc = "SoilBlock, fertility: " + f + "/15";
            blockColor = f == 0 ? ChatFormatting.RED
                       : f < 4  ? ChatFormatting.GOLD
                       :          ChatFormatting.WHITE;
        } else if (blockState.getBlock() instanceof DynamicSaplingBlock) {
            fertility = null;
            blockDesc = "DynamicSaplingBlock (not yet transitioned to tree)";
            blockColor = ChatFormatting.WHITE;
        } else {
            fertility = null;
            blockDesc = "other (" + blockState.getBlock().getDescriptionId()
                      + ") - registry entry is stale";
            blockColor = ChatFormatting.RED;
        }

        source.sendSuccess(() -> Component.literal(
                "  block              : " + blockDesc)
                .withStyle(blockColor), false);

        if (fertility != null && fertility == 0) {
            source.sendSuccess(() -> Component.literal(
                    "  note               : fertility is 0 -- DT.Species.grow will "
                    + "skip this tree until fertility > 0 (bonemeal it, or it was choked).")
                    .withStyle(ChatFormatting.RED), false);
        }

        source.sendSuccess(() -> Component.literal(
                "  lastCallGameTime   : " + state.getLastCallGameTime()
                + "  (delta: " + callDelta + " ticks / "
                + String.format("%.1f", callDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);

        source.sendSuccess(() -> Component.literal(
                "  lastGrowthGameTime : " + state.getLastGrowthGameTime()
                + "  (delta: " + growthDelta + " ticks / "
                + String.format("%.1f", growthDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);

        source.sendSuccess(() -> Component.literal(
                "  avgGrowthInterval  : " + avgGrowthInterval + " ticks (config)")
                .withStyle(ChatFormatting.WHITE), false);

        source.sendSuccess(() -> Component.literal(
                "  catch-up steps?    : " + (wouldTrigger ? "YES (" + catchUpSteps + " steps)" : "no")
                + "  (need callDelta>" + (TreeCatchUp.AVG_CALL_TICK_INTERVAL * 2)
                + " growthDelta>" + (avgGrowthInterval * 2) + ")")
                .withStyle(wouldTrigger ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

        return 1;
    }
}
