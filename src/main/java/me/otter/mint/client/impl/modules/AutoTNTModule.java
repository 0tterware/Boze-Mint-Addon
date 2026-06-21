package me.otter.mint.client.impl.modules;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventInteract;
import dev.boze.api.option.*;
import dev.boze.api.utility.ChatHelper;
import dev.boze.api.utility.EntityHelper;
import dev.boze.api.utility.MathHelper;
import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.*;
import me.otter.mint.Mint;
import me.otter.mint.client.core.feature.RenderSettings;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AutoTNTModule extends AddonModule {

    private enum SortMode { Closest, Furthest, Random }

    private final ModeOption<InteractionMode> interactionMode = new ModeOption<>(this, "Interaction Mode", "Placement interaction style.", InteractionMode.NCP);
    private final SliderOption placeRange = new SliderOption(this, "Range", "Max search radius (blocks) for TNT placement spots.", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption minDistance = new SliderOption(this, "MinDistance", "Minimum distance from you to place TNT.", 0.0, 0.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Raycast reach for placement (horizontal/vertical).", 4.0, 1.0, 6.0, 0.1);
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between TNT attempts.", 1.0, 0.0, 8.0, 1.0);
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate to face TNT placement.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDirection", "Stricter checks for rotation", true);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "How to swap TNT into hand", ToggleableSwapType.Silent);
    private final SliderOption horizontalSpread = new SliderOption(this, "HorizontalSpread", "Blocks horizontally to keep away from existing/just-placed TNT.", 1.0, 0.0, 3.0, 1.0);
    private final SliderOption verticalSpread = new SliderOption(this, "VerticalSpread", "Blocks vertically to keep away from existing/just-placed TNT.", 1.0, 0.0, 3.0, 1.0);
    private final ModeOption<SortMode> sortMode = new ModeOption<>(this, "SortMode", "Choose which candidate we try first.", SortMode.Furthest);
    private final ToggleOption autoDisable = new ToggleOption(this, "AutoDisable", "Turn module off if no TNT is found.", false);
    private final ToggleOption onlyStill = new ToggleOption(this, "OnlyStill", "Don't place while moving.", false);
    private final ToggleOption debug = new ToggleOption(this, "Debug", "Debug messages in chat/log.", false);
    private final RenderSettings placements = new RenderSettings(this);

    private static final long PLACEMENT_MEMORY_MS = 2500L;

    private int ticksSincePlace = 0;
    private final List<BlockPos> candidates = new ArrayList<>();
    private final Map<BlockPos, Long> recentPlacements = new HashMap<>();
    private final Random rng = new Random();

    public AutoTNTModule() {
        super("AutoTNT", "Automatically places TNT around you.");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        ticksSincePlace = 0;
        candidates.clear();
        recentPlacements.clear();
    }

    private boolean canAttemptPlace() {
        if (Mint.mc.player == null || Mint.mc.world == null) return false;
        return !onlyStill.getValue() || !EntityHelper.isMoving(Mint.mc.player);
    }

    private void refreshCandidateList() {
        candidates.clear();

        if (!canAttemptPlace()) return;

        // age out remembered placements so spots free up again after the TNT is gone
        long now = System.currentTimeMillis();
        recentPlacements.entrySet().removeIf(entry -> now - entry.getValue() > PLACEMENT_MEMORY_MS);

        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        final double max = placeRange.getValue();
        final double effMin = MathHelper.clamp(minDistance.getValue(), 0.0, max);
        final double maxSq = max * max;
        final double minSq = effMin * effMin;

        final BlockPos base = Mint.mc.player.getBlockPos();
        final int r = (int) Math.ceil(max);
        final int hSpread = horizontalSpread.getValue().intValue();
        final int vSpread = verticalSpread.getValue().intValue();

        for (int ox = -r; ox <= r; ox++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int oz = -r; oz <= r; oz++) {
                    BlockPos pos = base.add(ox, oy, oz);

                    // distance ring
                    Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double distSq = center.squaredDistanceTo(eye);
                    if (distSq > maxSq || distSq < minSq) continue;

                    // world checks
                    if (!WorldHelper.isInWorldBounds(pos)) continue;
                    if (!WorldHelper.isRegionLoaded(pos)) continue;
                    if (!WorldHelper.isReplaceable(pos)) continue;
                    if (!WorldHelper.canPlaceAt(pos)) continue;
                    if (!WorldHelper.isValidPlacement(pos, Blocks.TNT)) continue;
                    if (!PlaceHelper.isEmpty(pos)) continue;

                    // dont waste TNT into a fluid
                    if (!WorldHelper.getBlockState(pos).getFluidState().isEmpty()) continue;

                    // keep clear of TNT
                    if (isNearExistingTNT(pos, hSpread, vSpread)) continue;
                    if (isNearRecentPlacement(pos, hSpread, vSpread)) continue;

                    candidates.add(pos);
                }
            }
        }

        sortCandidates(eye);
    }

    private boolean isNearExistingTNT(BlockPos pos, int hSpread, int vSpread) {
        for (int sx = -hSpread; sx <= hSpread; sx++) {
            for (int sy = -vSpread; sy <= vSpread; sy++) {
                for (int sz = -hSpread; sz <= hSpread; sz++) {
                    if (sx == 0 && sy == 0 && sz == 0) continue;
                    if (WorldHelper.getBlockState(pos.add(sx, sy, sz)).isOf(Blocks.TNT)) return true;
                }
            }
        }
        return false;
    }

    private boolean isNearRecentPlacement(BlockPos pos, int hSpread, int vSpread) {
        for (BlockPos placed : recentPlacements.keySet()) {
            if (Math.abs(pos.getX() - placed.getX()) <= hSpread
                    && Math.abs(pos.getZ() - placed.getZ()) <= hSpread
                    && Math.abs(pos.getY() - placed.getY()) <= vSpread) {
                return true;
            }
        }
        return false;
    }

    private void sortCandidates(Vec3d eye) {
        if (sortMode.getValue() == SortMode.Random) {
            Collections.shuffle(candidates, rng);
            return;
        }

        candidates.sort((a, b) -> {
            Vec3d ca = new Vec3d(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
            Vec3d cb = new Vec3d(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);

            double da = ca.squaredDistanceTo(eye);
            double db = cb.squaredDistanceTo(eye);

            int cmp = Double.compare(da, db);
            if (sortMode.getValue() == SortMode.Furthest) cmp = -cmp;
            if (cmp != 0) return cmp;

            // stable tie-break so the chosen target doesn't jitter between ticks
            int yCmp = Integer.compare(a.getY(), b.getY());
            if (yCmp != 0) return yCmp;
            int xCmp = Integer.compare(a.getX(), b.getX());
            if (xCmp != 0) return xCmp;
            return Integer.compare(a.getZ(), b.getZ());
        });
    }

    private boolean haveUsableTNT() {
        if (Mint.mc.player == null) return false;

        if (swapMode.getValue() == ToggleableSwapType.Off) {
            return Mint.mc.player.getMainHandStack() != null && Mint.mc.player.getMainHandStack().isOf(Blocks.TNT.asItem());
        }

        if (swapMode.getValue() == ToggleableSwapType.Alt) {
            return InvHelper.find(Blocks.TNT) != -1;
        }

        return InvHelper.findInHotbar(Blocks.TNT) != -1;
    }

    private void tryQueuePlace(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;
        if (candidates.isEmpty()) return;

        if (ticksSincePlace < placeDelay.getValue()) return;
        if (!canAttemptPlace()) return;
        if (event.getMode() != interactionMode.getValue()) return;

        int tntSlot = -1;
        boolean requiresSwap = true;

        if (swapMode.getValue() == ToggleableSwapType.Off) {
            requiresSwap = false;
            if (!haveUsableTNT()) {
                if (autoDisable.getValue()) this.setState(false);
                return;
            }
        } else if (swapMode.getValue() == ToggleableSwapType.Alt) {
            tntSlot = InvHelper.find(Blocks.TNT);
        } else {
            tntSlot = InvHelper.findInHotbar(Blocks.TNT);
        }

        if (requiresSwap && tntSlot == -1) {
            if (autoDisable.getValue()) this.setState(false);
            return;
        }

        BlockPos winPos = null;
        BlockHitResult hit = null;
        for (BlockPos pos : candidates) {
            BlockHitResult result = PlaceHelper.cast(pos, interactionMode.getValue(), placeRange.getValue(), wallsRange.getValue(), strictDirection.getValue());
            if (result != null) {
                winPos = pos;
                hit = result;
                break;
            }
        }

        if (hit == null) return;

        Runnable placeAction = getPlaceAction(tntSlot, hit, winPos);

        if (rotate.getValue()) {
            float[] rot = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), hit.getPos());
            event.addInteraction(new Interaction(placeAction, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(placeAction));
        }

        recentPlacements.put(winPos, System.currentTimeMillis());
        ticksSincePlace = 0;
    }

    private Runnable getPlaceAction(int tntSlot, BlockHitResult hit, BlockPos winPos) {
        return () -> {
            if (swapMode.getValue() != ToggleableSwapType.Off && tntSlot != -1) {
                InvHelper.swapToSlot(tntSlot, swapMode.getValue());
            }

            PlaceHelper.place(interactionMode.getValue(), hit, Hand.MAIN_HAND);

            if (swapMode.getValue() != ToggleableSwapType.Off && tntSlot != -1) {
                InvHelper.swapBack();
            }

            placements.render(hit);

            if (debug.getValue()) ChatHelper.sendMsg(this.getName(), "Placed TNT at " + winPos.toShortString());
        };
    }

    @Override
    public String getArrayListInfo() {
        if (Mint.mc.player == null) return "";
        return String.valueOf(countTNT());
    }

    private int countTNT() {
        int count = 0;
        PlayerInventory inv = Mint.mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(Blocks.TNT.asItem())) count += stack.getCount();
        }
        return count;
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;

        ticksSincePlace++;

        int recomputeEvery = Math.max(1, (int) Math.round(placeDelay.getValue()));
        if (candidates.isEmpty() || (ticksSincePlace % recomputeEvery) == 0) {
            refreshCandidateList();
        }

        tryQueuePlace(event);
    }
}
