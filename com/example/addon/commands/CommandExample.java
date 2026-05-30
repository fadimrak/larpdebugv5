package com.example.addon.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.class_2172;

public class CommandExample
extends Command {
    public CommandExample() {
        super("example", "Sends a message.", new String[0]);
    }

    public void build(LiteralArgumentBuilder<class_2172> builder) {
        builder.executes(context -> {
            this.info("hi", new Object[0]);
            return 1;
        });
        builder.then(CommandExample.literal((String)"name").then(CommandExample.argument((String)"nameArgument", (ArgumentType)StringArgumentType.word()).executes(context -> {
            String argument = StringArgumentType.getString((CommandContext)context, (String)"nameArgument");
            this.info("hi, " + argument, new Object[0]);
            return 1;
        })));
    }
}
