package me.otter.mint.client.impl.hud;

import dev.boze.api.addon.AddonHudModule;
import dev.boze.api.option.ColorOption;
import dev.boze.api.render.ClientColor;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.render.HudLine;
import dev.boze.api.render.HudText;
import me.otter.mint.Mint;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DurabilityHudModule extends AddonHudModule {

    private static final ClientColor LABEL = ColorMaker.staticColor(170, 170, 170);

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final ColorOption fullColor = new ColorOption(this, "FullColor", "Color at full durability.", ColorMaker.staticColor(85, 255, 85), 1.0f);
    private final ColorOption emptyColor = new ColorOption(this, "EmptyColor", "Color at no durability / no armor.", ColorMaker.staticColor(255, 85, 85), 1.0f);

    public DurabilityHudModule() {
        super("Durability", "Shows armor durability.");
    }

    @Override
    public List<HudLine> getLines() {
        List<HudLine> lines = new ArrayList<>();

        Player player = Mint.mc.player;
        if (player == null) return lines;

        ClientColor empty = emptyColor.getValue().color;
        ClientColor full = fullColor.getValue().color;

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = player.getItemBySlot(SLOTS[i]);

            String value;
            ClientColor color;
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
                value = "-";
                color = empty;
            } else {
                float percent = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
                percent = Math.max(0.0f, Math.min(1.0f, percent));
                value = Math.round(percent * 100.0f) + "%";
                color = lerp(empty, full, percent);
            }

            lines.add(HudLine.of(new HudText(value, color)));
        }

        return lines;
    }

    private static ClientColor lerp(ClientColor from, ClientColor to, float t) {
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return ColorMaker.staticColor(r, g, b);
    }

    @Override
    public boolean sortByLength() {
        return false;
    }
}
