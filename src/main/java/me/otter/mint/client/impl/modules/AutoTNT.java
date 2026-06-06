package me.otter.mint.client.impl.modules;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventInteract;
import dev.boze.api.event.EventWorldRender;
import dev.boze.api.option.*;
import dev.boze.api.render.ColorMaker;
import dev.boze.api.render.WorldDrawer;
import dev.boze.api.utility.ChatHelper;
import dev.boze.api.utility.EntityHelper;
import dev.boze.api.utility.MathHelper;
import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.*;
import me.otter.mint.Mint;
import me.otter.mint.client.core.PlacementRenderGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AutoTNT extends AddonModule {

    private enum SortMode { Closest, Furthest, Random }

    private final ModeOption<InteractionMode> interactionMode = new ModeOption<>(this, "Interaction Mode", "Placement interaction style.", InteractionMode.NCP);
    private final SliderOption placeRange = new SliderOption(this, "Range", "Max search radius (blocks) for TNT placement spots.", 5.0, 2.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Raycast reach for placement (horizontal/vertical).", 4.0, 1.0, 6.0, 0.1);
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between TNT attempts.", 1.0, 0.0, 8.0, 1.0);
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate to face TNT placement.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDirection", "Stricter checks for rotation", true);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "How to swap TNT into hand", ToggleableSwapType.Silent);
    private final SliderOption horizontalSpread = new SliderOption(this, "HorizontalSpread", "Blocks horizontally to avoid placing next to existing TNT.", 1.0, 0.0, 3.0, 1.0);
    private final SliderOption verticalSpread = new SliderOption(this, "VerticalSpread", "Blocks vertically to avoid placing next to existing TNT.", 1.0, 0.0, 3.0, 1.0);
    private final ModeOption<SortMode> sortMode = new ModeOption<>(this, "SortMode", "Choose which candidate we try first.", SortMode.Furthest);
    private final ToggleOption renderPotential = new ToggleOption(this, "RenderPotential", "Show every valid TNT spot.", false);
    private final ColorOption potentialColor = new ColorOption(this, "PotentialColor", "Color for all valid TNT spots.", ColorMaker.staticColor(255, 0, 0), 0.2f, 0.6f);
    private final ToggleOption renderCurrent = new ToggleOption(this, "RenderCurrent", "Highlight the spot we're about to place.", false);
    private final ColorOption currentColor = new ColorOption(this, "CurrentColor", "Color for the chosen TNT placement.", ColorMaker.staticColor(200, 200, 200), 0.2f, 0.6f);
    private final ToggleOption autoDisable = new ToggleOption(this, "AutoDisable", "Turn module off if no TNT is found.", false);
    private final ToggleOption onlyStill = new ToggleOption(this, "OnlyStill", "Don't place while moving.", false);
    private final ToggleOption debug = new ToggleOption(this, "Debug", "Debug messages in chat/log.", false);
    private final PlacementRenderGroup placements = new PlacementRenderGroup(this);

    private int ticksSincePlace = 0;
    private final List<BlockPos> previewPositions = new ArrayList<>();
    private BlockPos targetPos = null;
    private final Random rng = new Random();

    public AutoTNT() {
        super("AutoTNT", "Automatically places TNT around you.");
    }

    @Override
    public void onEnable() {
        ticksSincePlace = 0;
        previewPositions.clear();
        targetPos = null;
    }

    @Override
    public void onDisable() {
        ticksSincePlace = 0;
        previewPositions.clear();
        targetPos = null;
    }

    private boolean canAttemptPlace() {
        if (Mint.mc.player == null || Mint.mc.world == null) return false;
        return !onlyStill.getValue() || !EntityHelper.isMoving(Mint.mc.player);
    }

    private void refreshCandidateList() {
        previewPositions.clear();
        targetPos = null;

        if (!canAttemptPlace()) return;

        final double searchRadius = placeRange.getValue();
        final BlockPos base = Mint.mc.player.getBlockPos();

        final int r = (int) Math.ceil(searchRadius);
        final int hSpread = horizontalSpread.getValue().intValue();
        final int vSpread = verticalSpread.getValue().intValue();

        for (int ox = -r; ox <= r; ox++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int oz = -r; oz <= r; oz++) {
                    BlockPos pos = base.add(ox, oy, oz);

                    // distance sphere check
                    Vec3d centerOfBlock = new Vec3d(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                    );
                    double distSq = centerOfBlock.squaredDistanceTo(Mint.mc.player.getEntityPos());
                    if (distSq > (searchRadius * searchRadius)) continue;

                    // world checks
                    if (!WorldHelper.isInWorldBounds(pos)) continue;
                    if (!WorldHelper.isRegionLoaded(pos)) continue;
                    if (!WorldHelper.isReplaceable(pos)) continue;
                    if (!WorldHelper.canPlaceAt(pos)) continue;
                    if (!WorldHelper.isValidPlacement(pos, Blocks.TNT)) continue;
                    if (!PlaceHelper.isEmpty(pos)) continue;

                    // fuck water
                    if (Mint.mc.world.getBlockState(pos).getBlock() instanceof Waterloggable) continue;
                    var blockAtPos = WorldHelper.getBlock(pos);
                    if (blockAtPos == Blocks.WATER || blockAtPos == Blocks.LAVA || blockAtPos == Blocks.BUBBLE_COLUMN) continue;

                    // don't place next to/inside TNT clusters
                    boolean tooCloseToExistingTNT = false;

                    for (int sx = -hSpread; sx <= hSpread && !tooCloseToExistingTNT; sx++) {
                        for (int sy = -vSpread; sy <= vSpread && !tooCloseToExistingTNT; sy++) {
                            for (int sz = -hSpread; sz <= hSpread; sz++) {
                                if (sx == 0 && sy == 0 && sz == 0) continue;

                                BlockPos near = pos.add(sx, sy, sz);

                                if (Mint.mc.world.getBlockState(near).isOf(Blocks.TNT)) {
                                    tooCloseToExistingTNT = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (tooCloseToExistingTNT) continue;

                    // check for cast
                    BlockHitResult hit = PlaceHelper.cast(
                            pos,
                            interactionMode.getValue(),
                            placeRange.getValue(),
                            wallsRange.getValue(),
                            strictDirection.getValue()
                    );
                    if (hit == null) continue;

                    previewPositions.add(pos);
                }
            }
        }

        sortCandidates();
        if (!previewPositions.isEmpty()) {
            targetPos = previewPositions.get(0);
        }
    }

    private void sortCandidates() {
        if (sortMode.getValue() == SortMode.Random) {
            Collections.shuffle(previewPositions, rng);
            return;
        }

        previewPositions.sort((a, b) -> {
            Vec3d ca = new Vec3d(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
            Vec3d cb = new Vec3d(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);

            double da = ca.squaredDistanceTo(Mint.mc.player.getEntityPos());
            double db = cb.squaredDistanceTo(Mint.mc.player.getEntityPos());

            int cmp = Double.compare(da, db);
            if (sortMode.getValue() == SortMode.Furthest) cmp = -cmp;

            if (cmp != 0) return cmp;

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
            int slot = InvHelper.find(Blocks.TNT);
            return slot != -1;
        }

        int slot = InvHelper.findInHotbar(Blocks.TNT);
        return slot != -1;
    }

    private void tryQueuePlace(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;
        if (targetPos == null) return;

        double requiredDelay = placeDelay.getValue();
        if (ticksSincePlace < requiredDelay) return;

        if (!canAttemptPlace()) return;

        if (event.getMode() != interactionMode.getValue()) return;

        int tntSlot = -1;
        boolean requiresSwap = true;

        if (swapMode.getValue() == ToggleableSwapType.Off) {
            requiresSwap = false;
            if (!haveUsableTNT()) {
                if (autoDisable.getValue()) {
                    this.setState(false);
                }
                return;
            }
        } else if (swapMode.getValue() == ToggleableSwapType.Alt) {
            tntSlot = InvHelper.find(Blocks.TNT);
        } else {
            tntSlot = InvHelper.findInHotbar(Blocks.TNT);
        }

        if (requiresSwap && tntSlot == -1) {
            if (autoDisable.getValue()) {
                this.setState(false);
            }
            return;
        }

        BlockHitResult hit = PlaceHelper.cast(targetPos, interactionMode.getValue(), placeRange.getValue(), wallsRange.getValue(), strictDirection.getValue());
        if (hit == null) return;

        Runnable placeAction = getPlaceAction(tntSlot, hit);

        if (rotate.getValue()) {
            Vec3d eyePos = EntityHelper.getEyePos(Mint.mc.player);
            float[] rot = MathHelper.calculateRotation(eyePos, hit.getPos());
            event.addInteraction(new Interaction(placeAction, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(placeAction));
        }

        ticksSincePlace = 0;
    }

    private Runnable getPlaceAction(int tntSlot, BlockHitResult hit) {
        // actually perform the swap/place/swapBack
        return () -> {
            if (swapMode.getValue() != ToggleableSwapType.Off) {
                InvHelper.swapToSlot(tntSlot, swapMode.getValue());
            }

            PlaceHelper.place(interactionMode.getValue(), hit, Hand.MAIN_HAND);

            if (swapMode.getValue() != ToggleableSwapType.Off) {
                InvHelper.swapBack();
            }

            placements.render(hit);

            if (debug.getValue()) ChatHelper.sendMsg(this.getName(), "Placed TNT at " + targetPos.toShortString());
        };
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;

        ticksSincePlace++;

        int recomputeEvery = Math.max(1, (int) Math.round(placeDelay.getValue()));
        if (targetPos == null || (ticksSincePlace % recomputeEvery) == 0) {
            refreshCandidateList();
        }

        tryQueuePlace(event);
    }

    @EventHandler
    private void onWorldRender(EventWorldRender event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;
        if (!haveUsableTNT()) return;

        WorldDrawer.start();

        if (renderPotential.getValue()) {
            for (BlockPos p : previewPositions) {
                Box bb = new Box(p);
                WorldDrawer.boxSides(potentialColor.getValue(), bb);
                WorldDrawer.boxLines(potentialColor.getValue(), bb);
            }
        }

        if (renderCurrent.getValue() && targetPos != null) {
            Box bb = new Box(targetPos);
            WorldDrawer.boxSides(currentColor.getValue(), bb);
            WorldDrawer.boxLines(currentColor.getValue(), bb);
        }
        WorldDrawer.draw(event.matrices);
    }
}