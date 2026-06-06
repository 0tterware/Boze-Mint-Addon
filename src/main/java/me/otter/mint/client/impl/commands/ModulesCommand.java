package me.otter.mint.client.impl.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.boze.api.addon.AddonCommand;
import dev.boze.api.addon.AddonModule;
import dev.boze.api.utility.ChatHelper;
import me.otter.mint.Mint;
import net.minecraft.command.CommandSource;

public class ModulesCommand extends AddonCommand {

    public ModulesCommand() {
        super(Mint.ID+"-modules", "Lists all modules of the addon");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            for (AddonModule module : Mint.INSTANCE.modules) {
                ChatHelper.sendMsg(module.getName() + " | " + module.getDescription());
            }
            return 1;
        });
    }
}
