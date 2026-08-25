/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 */
package com.theflex5710.oresim.commands;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.theflex5710.oresim.utils.SeedStore;
import net.minecraft.client.Minecraft;

public class SeedCommand extends Command {
    private static LiteralArgumentBuilder<AutismCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<AutismCommandSource, T> argument(
        String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public SeedCommand() {
        super("seed-world", "Get or set the seed for the current world.", "sw");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> builder) {
        builder.executes(ctx -> {
            Minecraft mc = ctx.getSource().mc();
            Long seed = SeedStore.current(mc);
            if (seed == null) {
                AutismClientMessaging.sendPrefixed("§cNo seed for current world saved.");
            } else {
                String scope = SeedStore.scopeKey(mc);
                AutismClientMessaging.sendPrefixed("§7[§a" + scope + "§7] seed: §a" + seed);
            }
            return SUCCESS;
        });

        builder.then(literal("list").executes(ctx -> {
            for (java.util.Map.Entry<String, Long> entry : SeedStore.stored()) {
                AutismClientMessaging.sendPrefixed("§7[§a" + entry.getKey() + "§7] seed: §a" + entry.getValue());
            }
            return SUCCESS;
        }));

        builder.then(literal("delete").executes(ctx -> {
            Minecraft mc = ctx.getSource().mc();
            if (SeedStore.clear(mc)) {
                AutismClientMessaging.sendPrefixed("§7Deleted stored seed.");
            } else {
                AutismClientMessaging.sendPrefixed("§cNothing to delete.");
            }
            return SUCCESS;
        }));

        builder.then(argument("seed", StringArgumentType.string()).executes(ctx -> {
            Minecraft mc = ctx.getSource().mc();
            String raw = StringArgumentType.getString(ctx, "seed");
            long numeric = parseSeed(raw);
            if (SeedStore.set(mc, numeric)) {
                AutismClientMessaging.sendPrefixed("§aSeed set to §f" + numeric + "§a.");
            } else {
                AutismClientMessaging.sendPrefixed("§cCannot set a seed here (singleplayer already exposes it).");
            }
            return SUCCESS;
        }));
    }

    private static long parseSeed(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return raw.strip().hashCode();
        }
    }
}
