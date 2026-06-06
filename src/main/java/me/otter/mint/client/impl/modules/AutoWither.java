package me.otter.mint.client.impl.modules;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventInteract;
import dev.boze.api.event.EventWorldRender;
import dev.boze.api.option.ColorOption;
import dev.boze.api.option.ModeOption;
import dev.boze.api.option.SliderOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.render.WorldDrawer;
import dev.boze.api.utility.ChatHelper;
import dev.boze.api.utility.EntityHelper;
import dev.boze.api.utility.MathHelper;
import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.*;
import me.otter.mint.client.core.PlacementRenderGroup;
import me.otter.mint.client.core.utils.WitherLayout;
import me.otter.mint.Mint;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AutoWither extends AddonModule {

    private enum RunMode { Once, Continuous }
    private enum SortMode { Closest, Furthest, Random }

    private final ModeOption<InteractionMode> interactionMode = new ModeOption<>(this, "Interaction Mode", "Placement interaction style.", InteractionMode.NCP);
    private final ModeOption<RunMode> runMode = new ModeOption<>(this, "Mode", "Run once or continuously.", RunMode.Once);
    private final ToggleOption mouseOnce = new ToggleOption(this, "OnceMouse", "In Once mode: build where you're looking.", true);
    private final ModeOption<SortMode> sortMode = new ModeOption<>(this, "SortMode", "Which valid layout to build first.", SortMode.Closest);
    private final SliderOption placeRange = new SliderOption(this, "Range", "Max radius to consider for layouts.", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Raycast reach (h/v).", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption minRange = new SliderOption(this, "MinRange", "Minimum distance from you (auto mode).", 1.0, 0.0, 4.0, 0.1);
    private final SliderOption cancelExtra = new SliderOption(this, "CancelExtra", "Abandon a summon if a block drifts this far past Range.", 2.0, 0.0, 6.0, 0.1);
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between block placements.", 1.0, 0.0, 8.0, 1.0);
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate towards target before placing.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDir", "Use stricter raycast direction.", true);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "Swap style for items.", ToggleableSwapType.Silent);
    private final ToggleOption render = new ToggleOption(this, "Render", "Render the blocks we still need to place.", true);
    private final ColorOption renderColor = new ColorOption(this, "RenderColor", "Color for preview blocks.", ColorMaker.staticColor(0, 255, 255), 0.2f, 0.6f);
    private final ToggleOption debug = new ToggleOption(this, "Debug", "Debug messages.", false);
    private final PlacementRenderGroup placements = new PlacementRenderGroup(this);

    private static final int STALL_LIMIT = 40;

    private final Random rng = new Random();

    private boolean summoning = false;
    private int ticksSincePlace = 0;
    private int stallTicks = 0;

    private final List<BlockPos> sandQueue = new ArrayList<>();
    private final List<BlockPos> skullQueue = new ArrayList<>();

    private BlockPos basePreview = null;
    private WitherLayout layoutPreview = null;

    public AutoWither() {
        super("AutoWither", "Automatically summons Withers.");
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
        summoning = false;
        ticksSincePlace = 0;
        stallTicks = 0;
        sandQueue.clear();
        skullQueue.clear();
        basePreview = null;
        layoutPreview = null;
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.world == null || Mint.mc.player == null) return;
        if (event.getMode() != interactionMode.getValue()) return;

        ticksSincePlace++;

        // choose a layout
        if (!summoning) {
            boolean gotLayout = (runMode.getValue() == RunMode.Once) ? pickLayoutOnceMode() : pickLayoutAutoMode();
            if (!gotLayout) {
                basePreview = null;
                layoutPreview = null;
                if (runMode.getValue() == RunMode.Once) {
                    if (debug.getValue()) ChatHelper.sendMsg(getName(), "No valid layout found.");
                    this.setState(false);
                }
                return;
            }
            startSummonFromPreview();
        }

        if (ticksSincePlace < placeDelay.getValue()) return;
        doPlaceStep(event);
    }

    private boolean pickLayoutOnceMode() {
        if (!mouseOnce.getValue()) return pickLayoutAutoMode();

        HitResult hr = Mint.mc.crosshairTarget;
        if (!(hr instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return false;

        BlockPos base = bhr.getBlockPos().offset(bhr.getSide());

        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        final double r2 = placeRange.getValue() * placeRange.getValue();
        final double minR2 = minRange.getValue() * minRange.getValue();

        for (WitherLayout l : WitherLayout.findLayoutsAt(base)) {
            List<BlockPos> souls = l.getSoulOffsets(base);
            List<BlockPos> skulls = l.getSkullOffsets(base);
            if (!withinRanges(eye, souls, skulls, r2, minR2)) continue;
            if (!canStart(souls)) continue;

            basePreview = base;
            layoutPreview = l;
            return true;
        }
        return false;
    }

    private boolean pickLayoutAutoMode() {
        final double r = placeRange.getValue();
        final int ri = (int) Math.ceil(r);
        final double r2 = r * r;
        final double minR2 = minRange.getValue() * minRange.getValue();
        final BlockPos center = Mint.mc.player.getBlockPos();
        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);

        List<Cand> cands = new ArrayList<>();

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos base = center.add(dx, dy, dz);

                    // sphere prune + cheap solid-terrain prune before the heavy layout scan
                    if (sqDistToCenter(eye, base) > r2) continue;
                    if (!WorldHelper.isReplaceable(base)) continue;

                    for (WitherLayout l : WitherLayout.findLayoutsAt(base)) {
                        List<BlockPos> souls = l.getSoulOffsets(base);
                        List<BlockPos> skulls = l.getSkullOffsets(base);
                        if (!withinRanges(eye, souls, skulls, r2, minR2)) continue;
                        cands.add(new Cand(base, l, souls, avgSqDist(eye, souls, skulls)));
                    }
                }
            }
        }

        if (cands.isEmpty()) return false;

        switch (sortMode.getValue()) {
            case Closest -> cands.sort(Comparator.comparingDouble(Cand::metric));
            case Furthest -> cands.sort((a, b) -> Double.compare(b.metric(), a.metric()));
            case Random -> Collections.shuffle(cands, rng);
        }

        for (Cand c : cands) {
            if (canStart(c.souls())) {
                basePreview = c.base();
                layoutPreview = c.layout();
                return true;
            }
        }
        return false;
    }

    private record Cand(BlockPos base, WitherLayout layout, List<BlockPos> souls, double metric) {}

    private static boolean withinRanges(Vec3d eye, List<BlockPos> souls, List<BlockPos> skulls, double r2, double minR2) {
        for (BlockPos p : souls) {
            double d2 = sqDistToCenter(eye, p);
            if (d2 > r2 || d2 < minR2) return false;
        }
        for (BlockPos p : skulls) {
            double d2 = sqDistToCenter(eye, p);
            if (d2 > r2 || d2 < minR2) return false;
        }
        return true;
    }

    private boolean canStart(List<BlockPos> souls) {
        for (BlockPos s : souls) {
            if (castFor(s) != null) return true;
        }
        return false;
    }

    private void startSummonFromPreview() {
        sandQueue.clear();
        skullQueue.clear();
        sandQueue.addAll(layoutPreview.getSoulOffsets(basePreview));
        skullQueue.addAll(layoutPreview.getSkullOffsets(basePreview));
        summoning = true;
        ticksSincePlace = 0;
        stallTicks = 0;

        if (debug.getValue()) ChatHelper.sendMsg(getName(), "Starting summon at " + basePreview.toShortString());
    }

    private void doPlaceStep(EventInteract event) {
        // Both queues empty
        if (sandQueue.isEmpty() && skullQueue.isEmpty()) {
            if (debug.getValue()) ChatHelper.sendMsg(getName(), "Summon complete.");
            finishOrDisable();
            return;
        }

        // Abandon if the player moved
        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        final double cancel = placeRange.getValue() + cancelExtra.getValue();
        if (anyOutOfReach(eye, cancel * cancel)) {
            abandon("Blocks out of reach.");
            return;
        }

        boolean skullPhase = sandQueue.isEmpty();
        List<BlockPos> pool = skullPhase ? skullQueue : sandQueue;

        Placeable target = pickNearestPlaceable(pool, skullPhase, eye);
        if (target == null) {
            if (++stallTicks > STALL_LIMIT) abandon("Stuck, abandoning layout.");
            return;
        }
        stallTicks = 0;

        int slot = -1;
        if (swapMode.getValue() == ToggleableSwapType.Off) {
            if (!holdingCorrect(skullPhase)) return;
        } else {
            slot = findSlot(skullPhase);
            if (slot == -1) {
                if (debug.getValue()) ChatHelper.sendMsg(getName(), "Missing materials. Disabling.");
                this.setState(false);
                return;
            }
        }

        final int placeSlot = slot;
        final BlockHitResult hit = target.hit();
        final BlockPos placedPos = target.pos();
        final boolean skull = skullPhase;
        Runnable placeAction = () -> {
            if (swapMode.getValue() != ToggleableSwapType.Off) InvHelper.swapToSlot(placeSlot, swapMode.getValue());

            PlaceHelper.place(interactionMode.getValue(), hit, Hand.MAIN_HAND);

            if (swapMode.getValue() != ToggleableSwapType.Off) InvHelper.swapBack();

            placements.render(hit);
            pool.remove(placedPos);

            if (debug.getValue()) ChatHelper.sendMsg(getName(), "Placed " + (skull ? "Skull" : "Soul") + " at " + placedPos.toShortString());
        };

        if (rotate.getValue()) {
            float[] rot = MathHelper.calculateRotation(eye, hit.getPos());
            event.addInteraction(new Interaction(placeAction, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(placeAction));
        }

        ticksSincePlace = 0;
    }

    private boolean anyOutOfReach(Vec3d eye, double cancelSq) {
        for (BlockPos p : sandQueue) if (sqDistToCenter(eye, p) > cancelSq) return true;
        for (BlockPos p : skullQueue) if (sqDistToCenter(eye, p) > cancelSq) return true;
        return false;
    }

    private Placeable pickNearestPlaceable(List<BlockPos> pool, boolean skullPhase, Vec3d eye) {
        return pool.stream()
                .sorted(Comparator.comparingDouble(p -> sqDistToCenter(eye, p)))
                .map(p -> new Placeable(p, skullPhase ? hitForSkull(p) : castFor(p)))
                .filter(pl -> pl.hit() != null)
                .findFirst()
                .orElse(null);
    }

    private record Placeable(BlockPos pos, BlockHitResult hit) {}

    private void finishOrDisable() {
        if (runMode.getValue() == RunMode.Once) {
            this.setState(false);
        } else {
            summoning = false;
            stallTicks = 0;
            sandQueue.clear();
            skullQueue.clear();
            basePreview = null;
            layoutPreview = null;
        }
    }

    private void abandon(String reason) {
        if (debug.getValue()) ChatHelper.sendMsg(getName(), reason);
        if (runMode.getValue() == RunMode.Once) {
            this.setState(false);
        } else {
            finishOrDisable();
        }
    }

    private BlockHitResult castFor(BlockPos pos) {
        return PlaceHelper.cast(pos, interactionMode.getValue(), placeRange.getValue(), wallsRange.getValue(), strictDirection.getValue());
    }

    private BlockHitResult hitForSkull(BlockPos skullPos) {
        if (!WorldHelper.isReplaceable(skullPos) || !PlaceHelper.isEmpty(skullPos)) return null;

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = skullPos.offset(dir);
            if (Mint.mc.world.getBlockState(neighbour).isOf(Blocks.SOUL_SAND)) {
                Vec3d hitVec = new Vec3d(neighbour.getX() + 0.5, neighbour.getY() + 0.5, neighbour.getZ() + 0.5);
                return new BlockHitResult(hitVec, dir.getOpposite(), neighbour, false);
            }
        }
        return null;
    }

    private boolean holdingCorrect(boolean skull) {
        var stack = Mint.mc.player.getMainHandStack();
        return stack != null && stack.isOf((skull ? Blocks.WITHER_SKELETON_SKULL : Blocks.SOUL_SAND).asItem());
    }

    private int findSlot(boolean skull) {
        var block = skull ? Blocks.WITHER_SKELETON_SKULL : Blocks.SOUL_SAND;
        return swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(block) : InvHelper.findInHotbar(block);
    }

    private static double sqDistToCenter(Vec3d eye, BlockPos p) {
        double dx = (p.getX() + 0.5) - eye.x;
        double dy = (p.getY() + 0.5) - eye.y;
        double dz = (p.getZ() + 0.5) - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double avgSqDist(Vec3d eye, List<BlockPos> souls, List<BlockPos> skulls) {
        double sum = 0.0;
        for (BlockPos p : souls) sum += sqDistToCenter(eye, p);
        for (BlockPos p : skulls) sum += sqDistToCenter(eye, p);
        int count = souls.size() + skulls.size();
        return count == 0 ? 0 : sum / count;
    }

    @EventHandler
    private void onWorldRender(EventWorldRender event) {
        if (!render.getValue()) return;
        if (Mint.mc.world == null || Mint.mc.player == null) return;
        if (!summoning) return;

        WorldDrawer.start();
        for (BlockPos p : sandQueue) drawBox(p);
        for (BlockPos p : skullQueue) drawBox(p);
        WorldDrawer.draw(event.matrices);
    }

    private void drawBox(BlockPos p) {
        Box bb = new Box(p);
        WorldDrawer.boxSides(renderColor.getValue(), bb);
        WorldDrawer.boxLines(renderColor.getValue(), bb);
    }
}
