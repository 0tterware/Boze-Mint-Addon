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
    private final ModeOption<SortMode> sortMode = new ModeOption<>(this, "SortMode", "Auto mode layout selection.", SortMode.Closest);
    private final SliderOption placeRange = new SliderOption(this, "Range", "Max radius to consider for layouts.", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Raycast reach (h/v).", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption minRange = new SliderOption(this, "MinRange", "Minimum distance from you (auto mode).", 1.0, 0.0, 4.0, 0.1);
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between block placements.", 1.0, 0.0, 8.0, 1.0);
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate towards target before placing.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDir", "Use stricter raycast direction.", true);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "Swap style for items.", ToggleableSwapType.Silent);
    private final ToggleOption render = new ToggleOption(this, "Render", "Render preview blocks.", true);
    private final ColorOption renderColor = new ColorOption(this, "RenderColor", "Color for all preview blocks.", ColorMaker.staticColor(0, 255, 255), 0.2f, 0.6f);
    private final ToggleOption debug = new ToggleOption(this, "Debug", "Debug messages.", false);
    private final PlacementRenderGroup placements = new PlacementRenderGroup(this);

    private final Random rng = new Random();

    private boolean summoning = false;
    private int ticksSincePlace = 0;

    private List<BlockPos> sandQueue = new ArrayList<>();
    private List<BlockPos> skullQueue = new ArrayList<>();

    private BlockPos basePreview = null;
    private WitherLayout layoutPreview = null;
    private BlockPos currentPos = null;

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
        sandQueue.clear();
        skullQueue.clear();
        basePreview = null;
        layoutPreview = null;
        currentPos = null;
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.world == null || Mint.mc.player == null) return;
        if (event.getMode() != interactionMode.getValue()) return;

        ticksSincePlace++;

        // If not currently summoning pick a layout according to mode
        if (!summoning) {
            boolean gotLayout = (runMode.getValue() == RunMode.Once) ? pickLayoutOnceMode() : pickLayoutAutoMode();

            if (!gotLayout) {
                if (runMode.getValue() == RunMode.Once) {
                    if (debug.getValue()) ChatHelper.sendMsg(getName(), "No valid layout found.");

                    this.setState(false);
                }
                return;
            }
            startSummonFromPreview();
        }

        // Place step
        if (ticksSincePlace < placeDelay.getValue()) return;
        doPlaceStep(event);
    }

    private boolean pickLayoutOnceMode() {
        if (mouseOnce.getValue()) {
            HitResult hr = Mint.mc.crosshairTarget;
            if (!(hr instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return false;

            Direction face = bhr.getSide();
            BlockPos base = bhr.getBlockPos().offset(face);

            List<WitherLayout> layouts = WitherLayout.findLayoutsAt(base);
            if (layouts.isEmpty()) return false;

            // Choose any valid layout that satisfies distance limits
            WitherLayout picked = layouts.stream()
                    .filter(l -> withinRanges(base, l))
                    .findFirst()
                    .orElse(null);

            if (picked == null) return false;

            basePreview = base;
            layoutPreview = picked;
            return true;
        } else {
            // pick auto layout
            return pickLayoutAutoMode();
        }
    }

    private boolean pickLayoutAutoMode() {
        final double r = placeRange.getValue();
        final int ri = (int) Math.ceil(r);
        final double r2 = r * r;
        final double minR2 = minRange.getValue() * minRange.getValue();
        final BlockPos center = Mint.mc.player.getBlockPos();
        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);

        record Cand(BlockPos base, WitherLayout layout, double metric) {}

        List<Cand> cands = new ArrayList<>();

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos base = center.add(dx, dy, dz);

                    // sphere check (base center to eye)
                    Vec3d blockCenter = new Vec3d(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5);
                    if (blockCenter.squaredDistanceTo(eye) > r2) continue;

                    List<WitherLayout> layouts = WitherLayout.findLayoutsAt(base);
                    if (layouts.isEmpty()) continue;

                    for (WitherLayout l : layouts) {
                        if (!withinRanges(base, l)) continue;

                        // average squared distance from eye across all soul+skull blocks
                        double metric = avgSqDist(eye, l.getSoulOffsets(base), l.getSkullOffsets(base));
                        cands.add(new Cand(base, l, metric));
                    }
                }
            }
        }

        if (cands.isEmpty()) return false;

        switch (sortMode.getValue()) {
            case Closest -> cands.sort(Comparator.comparingDouble(c -> c.metric));
            case Furthest -> cands.sort((a,b) -> Double.compare(b.metric, a.metric));
            case Random -> {
                Collections.shuffle(cands, rng);
            }
        }

        Cand chosen = cands.get(0);
        basePreview = chosen.base;
        layoutPreview = chosen.layout;
        return true;
    }

    private boolean withinRanges(BlockPos base, WitherLayout l) {
        final double r2 = placeRange.getValue() * placeRange.getValue();
        final double minR2 = minRange.getValue() * minRange.getValue();
        final Vec3d eye = Mint.mc.player.getEntityPos().add(0, Mint.mc.player.getEyeHeight(Mint.mc.player.getPose()), 0);

        List<BlockPos> all = new ArrayList<>(l.getSoulOffsets(base));
        all.addAll(l.getSkullOffsets(base));

        for (BlockPos p : all) {
            double d2 = sqDistToCenter(eye, p);
            if (d2 > r2) return false;
            if (d2 < minR2) return false;
        }

        for (BlockPos soul : l.getSoulOffsets(base)) {
            if (hasSolidNeighbor(soul)) return true;
        }
        return false;
    }

    private static boolean hasSolidNeighbor(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (!WorldHelper.isReplaceable(n)) return true;
        }
        return false;
    }

    private static double avgSqDist(Vec3d eye, List<BlockPos> souls, List<BlockPos> skulls) {
        double sum = 0.0;
        int count = 0;

        for (BlockPos p : souls) {
            sum += sqDistToCenter(eye, p);
            count++;
        }

        for (BlockPos p : skulls) {
            sum += sqDistToCenter(eye, p);
            count++;
        }

        return count == 0 ? 0 : sum / count;
    }

    private static double sqDistToCenter(Vec3d eye, BlockPos p) {
        double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
        double dx = cx - eye.x, dy = cy - eye.y, dz = cz - eye.z;
        return dx*dx + dy*dy + dz*dz;
    }

    private void startSummonFromPreview() {
        sandQueue = new ArrayList<>(layoutPreview.getSoulOffsets(basePreview));
        skullQueue = new ArrayList<>(layoutPreview.getSkullOffsets(basePreview));
        summoning = true;
        ticksSincePlace = 0;
        currentPos = null;

        if (debug.getValue()) {
            ChatHelper.sendMsg(getName(), "Starting summon at " + basePreview.toShortString());
        }
    }

    private void doPlaceStep(EventInteract event) {
        if (Mint.mc.player == null) return;

        boolean skullPhase = sandQueue.isEmpty();
        List<BlockPos> pool = skullPhase ? skullQueue : sandQueue;
        if (pool.isEmpty()) {
            // Summon complete
            if (debug.getValue()) ChatHelper.sendMsg(getName(), "Summon complete.");
            if (runMode.getValue() == RunMode.Once) {
                this.setState(false);
            } else {
                // continuous: try to start a new one
                summoning = false;
                basePreview = null;
                layoutPreview = null;
            }
            return;
        }

        // Pick nearest placeable in pool
        BlockPos next = pickNearestPlaceable(pool, skullPhase);
        if (next == null) return;

        // Prepare hit
        BlockHitResult hit = skullPhase ? hitAgainstSoulSand(next) : castFor(next);
        if (hit == null) return;

        // Ensure we can place now
        int slot = resolveSlotFor(skullPhase);
        if (slot == -1 && swapMode.getValue() != ToggleableSwapType.Off) return;

        Runnable placeAction = () -> {
            if (swapMode.getValue() != ToggleableSwapType.Off && slot != -1)
                InvHelper.swapToSlot(slot, swapMode.getValue());

            PlaceHelper.place(interactionMode.getValue(), hit, Hand.MAIN_HAND);

            if (swapMode.getValue() != ToggleableSwapType.Off && slot != -1)
                InvHelper.swapBack();

            placements.render(hit);

            pool.remove(next);
            if (debug.getValue()) ChatHelper.sendMsg(getName(), "Placed " + (skullPhase ? "Skull" : "Soul") + " at " + next.toShortString());
        };

        if (rotate.getValue()) {
            Vec3d eye = Mint.mc.player.getEntityPos().add(0, Mint.mc.player.getEyeHeight(Mint.mc.player.getPose()), 0);
            float[] rot = MathHelper.calculateRotation(eye, hit.getPos());
            event.addInteraction(new Interaction(placeAction, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(placeAction));
        }

        currentPos = next;
        ticksSincePlace = 0;
    }

    private BlockPos pickNearestPlaceable(List<BlockPos> pool, boolean skullPhase) {
        if (pool.isEmpty()) return null;
        Vec3d eye = Mint.mc.player.getEntityPos().add(0, Mint.mc.player.getEyeHeight(Mint.mc.player.getPose()), 0);

        return pool.stream()
                .sorted(Comparator.comparingDouble(p -> sqDistToCenter(eye, p)))
                .filter(p -> skullPhase ? canPlaceSkullAt(p) : canPlaceSoulAt(p))
                .findFirst()
                .orElse(null);
    }

    private boolean canPlaceSoulAt(BlockPos pos) {
        if (!WorldHelper.isReplaceable(pos)) return false;
        if (!PlaceHelper.isEmpty(pos)) return false;
        return castFor(pos) != null;
    }

    private boolean canPlaceSkullAt(BlockPos skullPos) {
        return hitAgainstSoulSand(skullPos) != null;
    }

    private BlockHitResult castFor(BlockPos pos) {
        // Use wallsRange for both axes; strictDirection as chosen
        return PlaceHelper.cast(
                pos,
                interactionMode.getValue(),
                placeRange.getValue(),
                wallsRange.getValue(),
                strictDirection.getValue()
        );
    }

    private BlockHitResult hitAgainstSoulSand(BlockPos skullPos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = skullPos.offset(dir);
            if (Mint.mc.world.getBlockState(neighbour).isOf(Blocks.SOUL_SAND)) {
                // Click the soul sand face that touches the skull position
                Vec3d hitVec = new Vec3d(neighbour.getX() + 0.5, neighbour.getY() + 0.5, neighbour.getZ() + 0.5);
                return new BlockHitResult(hitVec, dir.getOpposite(), neighbour, false);
            }
        }
        return null;
    }

    private boolean anyPlaceableRemaining() {
        for (BlockPos p : sandQueue) {
            if (canPlaceSoulAt(p)) return true;
        }
        for (BlockPos p : skullQueue) {
            if (canPlaceSkullAt(p)) return true;
        }
        return false;
    }

    private int resolveSlotFor(boolean skull) {
        ToggleableSwapType mode = swapMode.getValue();

        if (mode == ToggleableSwapType.Off) {
            // must already be holding the correct item
            if (skull) {
                return Mint.mc.player.getMainHandStack() != null
                        && Mint.mc.player.getMainHandStack().isOf(Blocks.WITHER_SKELETON_SKULL.asItem()) ? -2 : -1;
            } else {
                return Mint.mc.player.getMainHandStack() != null
                        && Mint.mc.player.getMainHandStack().isOf(Blocks.SOUL_SAND.asItem()) ? -2 : -1;
            }
        }

        if (mode == ToggleableSwapType.Alt) {
            return InvHelper.find(skull ? Blocks.WITHER_SKELETON_SKULL : Blocks.SOUL_SAND);
        }

        // Silent / Normal -> hotbar
        return InvHelper.findInHotbar(skull ? Blocks.WITHER_SKELETON_SKULL : Blocks.SOUL_SAND);
    }

    @EventHandler
    private void onWorldRender(EventWorldRender event) {
        if (!render.getValue()) return;
        if (Mint.mc.world == null || Mint.mc.player == null) return;

        WorldDrawer.start();

        // Render remaining target blocks for current summon
        for (BlockPos p : sandQueue) {
            Box bb = new Box(p);
            WorldDrawer.boxSides(renderColor.getValue(), bb);
            WorldDrawer.boxLines(renderColor.getValue(), bb);
        }
        for (BlockPos p : skullQueue) {
            Box bb = new Box(p);
            WorldDrawer.boxSides(renderColor.getValue(), bb);
            WorldDrawer.boxLines(renderColor.getValue(), bb);
        }

        WorldDrawer.draw(event.matrices);
    }
}