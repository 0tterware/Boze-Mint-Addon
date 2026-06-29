package me.otter.mint.client.impl.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.boze.api.addon.AddonCommand;
import dev.boze.api.utility.ChatHelper;
import me.otter.mint.Mint;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Random;

public class CoinFlipCommand extends AddonCommand {

    private final Random random = new Random();

    public CoinFlipCommand() {
        super(Mint.ID+"-coinflip", "See if luck is on your side.");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            boolean rand = random.nextBoolean();

            if (rand) {
                String msg = String.format("Hey guys! %d, %d, %d", 0, 0, 0
                        // (int) Mint.mc.player.getX(),
                        // (int) Mint.mc.player.getY(),
                        // (int) Mint.mc.player.getZ()
                );
                Mint.mc.getConnection().sendChat(msg);
            } else {
                ChatHelper.sendMsg("Coinflip", "You won c:");
            }

            return 1;
        });
    }
}
