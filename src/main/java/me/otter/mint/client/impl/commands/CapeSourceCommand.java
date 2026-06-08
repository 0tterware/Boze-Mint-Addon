package me.otter.mint.client.impl.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.boze.api.addon.AddonCommand;
import dev.boze.api.utility.ChatHelper;
import dev.boze.api.utility.cape.CapesManager;
import me.otter.mint.Mint;
import net.minecraft.command.CommandSource;

import java.util.Random;

public class CapeSourceCommand extends AddonCommand {

    private final Random random = new Random();

    public CapeSourceCommand() {
        super(Mint.ID + "-capesources", "List all cape sources");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            for (String source : CapesManager.getSources()) {
                ChatHelper.sendMsg("CapeSource", source);
            }
            return 1;
        });
    }
}