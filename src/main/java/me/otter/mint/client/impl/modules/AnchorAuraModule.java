package me.otter.mint.client.impl.modules;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventBind;
import dev.boze.api.event.EventInteract;
import dev.boze.api.option.*;
import dev.boze.api.render.PlaceRenderer;
import dev.boze.api.utility.EntityHelper;
import dev.boze.api.utility.MathHelper;
import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.InteractionMode;
import dev.boze.api.utility.interaction.Interaction;
import dev.boze.api.utility.interaction.InvHelper;
import dev.boze.api.utility.interaction.PlaceHelper;
import dev.boze.api.utility.interaction.ToggleableSwapType;
import me.otter.mint.Mint;
import me.otter.mint.client.core.feature.RenderSettings;
import me.otter.mint.client.core.utils.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public class AnchorAuraModule extends AddonModule {

    private enum TargetMode { Closest, Furthest, LowestHealth, LowestArmor }
    private enum AimPoint { Center, Nearest }

    // General
    private final ModeOption<InteractionMode> antiCheat = new ModeOption<>(this, "AntiCheat", "Interaction mode.", InteractionMode.NCP);
    private final SliderOption range = new SliderOption(this, "Range", "Reach for placing/charging anchors.", 4.5, 1.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Reach through walls (NCP only).", 4.0, 0.0, 6.0, 0.1);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "How to swap items into hand.", ToggleableSwapType.Silent);
    private final ToggleOption multiTask = new ToggleOption(this, "MultiTask", "Run while using items.", false);
    private final ToggleOption pauseOnUse = new ToggleOption(this, "PauseOnUse", "Pause while using an item", true);
    private final ToggleOption fakeAir = new ToggleOption(this, "FakeAir", "Prevent ghost block rendering by setting block to air clientside", true);

    // Rotate
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate to the anchor before placing/interacting.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDirection", "Pass strict direction checks for placements.", true, rotate::getValue, rotate);
    private final ModeOption<AimPoint> aimPoint = new ModeOption<>(this, "AimPoint", "Where on the anchor to aim.", AimPoint.Center, rotate::getValue, rotate);

    private final RenderSettings render = new RenderSettings(this, "Render");

    // Targeting
    private final PageOption targeting = new PageOption(this, "Targeting", "Target selection.");
    private final ModeOption<TargetMode> targetMode = new ModeOption<>(this, "Mode", "Algorithm for selecting targets.", TargetMode.Closest, targeting);
    private final SliderOption targetRange = new SliderOption(this, "TargetRange", "Range within which to select targets.", 8.0, 1.0, 16.0, 0.5, targeting);
    private final SliderOption anchorRange = new SliderOption(this, "AnchorRange", "Max distance from a target to place anchors.", 4.0, 1.0, 8.0, 0.5, targeting);
    private final SliderOption maxTargets = new SliderOption(this, "MaxTargets", "Max targets to consider.", 3.0, 1.0, 8.0, 1.0, targeting);

    // Place
    private final PageOption behavior = new PageOption(this, "Behavior", "How to anchor");
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between placements.", 0.0, 0.0, 10.0, 1.0, behavior);
    private final SliderOption chargeDelay = new SliderOption(this, "ChargeDelay", "Ticks to wait after placing before charging (0 = same tick).", 0.0, 0.0, 10.0, 1.0, behavior);
    private final SliderOption explodeDelay = new SliderOption(this, "ExplodeDelay", "Ticks to wait after charging before detonating (0 = same tick).", 0.0, 0.0, 10.0, 1.0, behavior);
    private final ToggleOption airPlace = new ToggleOption(this, "AirPlace", "Allow placing against air.", false, behavior);
    private final ToggleOption instant = new ToggleOption(this, "Instant", "Re-place the anchor the same tick it detonates.", true, behavior);
    private final ToggleOption validate = new ToggleOption(this, "Validate", "Verify live block state before each place/charge/detonate", true, behavior);
    private final SliderOption stickBonus = new SliderOption(this, "StickBonus", "Extra damage a new spot must beat to abandon the current/last spot (0 = always switch to the strictly best spot).", 6.0, 0.0, 36.0, 0.5, behavior);

    // Damage / Safety
    private final PageOption damage = new PageOption(this, "Damage", "Damage thresholds and self safety.");
    private final SliderOption minDamage = new SliderOption(this, "MinDamage", "Minimum damage to place/charge/detonate an anchor.", 6.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption lethalHP = new SliderOption(this, "LethalHP", "Ignore min damage when target health (+absorption) is at/below this (face-place).", 0.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption maxSelfDamage = new SliderOption(this, "MaxSelfDamage", "Maximum self damage allowed.", 8.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption minHealth = new SliderOption(this, "MinHealth", "Don't act while your health (+absorption) is at/below this.", 4.0, 0.0, 36.0, 0.5, damage);
    private final ToggleOption antiSuicide = new ToggleOption(this, "AntiSuicide", "Never act if the predicted self damage would drop you to/below MinHealth.", true, damage);
    private final SliderOption balance = new SliderOption(this, "Balance", "Minimum enemy/self damage ratio (0 = off).", 0.0, 0.0, 10.0, 0.1, damage);
    private final ToggleOption assumeMaxArmor = new ToggleOption(this, "AssumeMaxArmor", "Assume targets have a full max-enchant armor set (for servers that hide armor info).", false, damage);
    private final ToggleOption predict = new ToggleOption(this, "Predict", "Extrapolate target movement for damage checks.", true, damage);
    private final BindOption override = new BindOption(this, "Override", "Hold to use OverrideMinDmg instead of MinDamage (more aggressive).", -1, false, damage);
    private final SliderOption overrideMinDmg = new SliderOption(this, "OverrideMinDmg", "MinDamage used while Override is held.", 1.0, 0.0, 36.0, 0.5, damage);

    // List
    private final PageOption list = new PageOption(this, "List", "ArrayList HUD info.");
    private final ToggleOption showTarget = new ToggleOption(this, "Target", "Show target name in ArrayList.", true, list);
    private final ToggleOption showDamage = new ToggleOption(this, "Damage", "Show target damage in ArrayList.", true, list);
    private final ToggleOption showCPS = new ToggleOption(this, "CPS", "Show detonations-per-second in ArrayList.", false, list);

    private static final double EXISTING_ANCHOR_BONUS = 1000.0;
    private static final double CHARGED_ANCHOR_BONUS = 2000.0;

    private final List<LivingEntity> targets = new ArrayList<>();
    private final Deque<Long> detonations = new ArrayDeque<>();

    private long tick = 0;
    private long lastPlaceTick = -1000L;
    private boolean overrideHeld = false;

    private BlockPos workSpot = null;
    private LivingEntity workTarget = null;
    private boolean workOwns = false;
    private boolean workPlaced = false;
    private boolean workCharged = false;
    private long workPlacedTick = 0;
    private long workChargedTick = 0;
    private long workSeenTick = 0;

    private LivingEntity displayTarget = null;
    private double displayDamage = 0;

    public AnchorAuraModule() {
        super("AnchorAura", "Automatically nukes enemies using respawn anchor explosions.");
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
        targets.clear();
        detonations.clear();
        displayTarget = null;
        displayDamage = 0;
        lastPlaceTick = -1000L;
        resetWork();
    }

    private void resetWork() {
        workSpot = null;
        workTarget = null;
        workOwns = false;
        workPlaced = false;
        workCharged = false;
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;
        if (event.getMode() != antiCheat.getValue()) return;
        if (Mint.mc.world.getRegistryKey().getValue().getPath().equals("the_nether")) return;
        if (!multiTask.getValue() && pauseOnUse.getValue() && Mint.mc.player.isUsingItem()) return;
        if (EntityHelper.getHealth(Mint.mc.player) <= minHealth.getValue()) return;

        tick++;
        updateCps();
        updateTargets();
        if (targets.isEmpty()) return;

        cycle(event);
    }

    private void cycle(EventInteract event) {
        if (!detonateAvailable()) return;

        if (workSpot != null && !workValid()) resetWork();

        if (!pickSpot()) { resetWork(); return; }

        final BlockPos spot = workSpot;
        final LivingEntity target = workTarget;
        displayTarget = target;

        final BlockHitResult face = computeAnchorFace(spot);
        if (face == null) { resetWork(); return; }
        final BlockState state = WorldHelper.getBlockState(spot);
        final boolean isAnchor = state.isOf(Blocks.RESPAWN_ANCHOR);

        if (isAnchor) workSeenTick = tick;
        else if (workPlaced && tick - workSeenTick > 20) { resetWork(); return; }

        final List<Runnable> steps = new ArrayList<>();
        final boolean[] placedNow = {false};
        final boolean[] chargedNow = {false};
        boolean placedThisTick = false;

        // 1) place
        if (!workPlaced) {
            if (isAnchor) {
                workPlaced = true;
                workPlacedTick = tick - (long) Math.ceil(chargeDelay.getValue());
            } else {
                if (tick - lastPlaceTick < placeDelay.getValue()) { flush(event, steps, spot); return; } // throttle placements
                if (!anchorAvailable()) { resetWork(); return; }
                BlockHitResult ph = PlaceHelper.cast(spot, airPlace.getValue(), antiCheat.getValue(),
                        range.getValue(), wallsRange.getValue(), strictDirection.getValue());
                if (ph == null) { resetWork(); return; } // new spot
                final int slot = findSlot(Blocks.RESPAWN_ANCHOR.asItem());
                steps.add(() -> placedNow[0] = placeAnchorAt(slot, spot, false));
                workOwns = true;
                placedThisTick = true;
                workPlaced = true;
                workPlacedTick = tick;
                lastPlaceTick = tick;
            }
        }

        // 2) charge
        if (workPlaced && !workCharged) {
            if (isAnchor && state.get(RespawnAnchorBlock.CHARGES) >= 1) {
                workCharged = true;
                workChargedTick = tick - (long) Math.ceil(explodeDelay.getValue());   // ready to detonate now
            } else {
                if (validate.getValue() && !isAnchor && !placedThisTick) { resetWork(); return; }
                if (tick - workPlacedTick < chargeDelay.getValue()) { flush(event, steps, spot); return; }
                if (!glowstoneAvailable()) { flush(event, steps, spot); resetWork(); return; }
                final int slot = findSlot(Items.GLOWSTONE);
                steps.add(() -> {
                    if (!canChargeNow(spot, placedNow[0])) return;
                    if (useOnBlock(slot, face, s -> s.isOf(Items.GLOWSTONE))) chargedNow[0] = true;
                });
                workCharged = true;
                workChargedTick = tick;
            }
        }

        // 3) detonate
        if (workCharged) {
            if (tick - workChargedTick < explodeDelay.getValue()) { flush(event, steps, spot); return; }
            workSeenTick = tick;
            final int slot = findDetonateSlot();
            final boolean[] detonatedNow = {false};
            steps.add(() -> {
                if (!canDetonateNow(spot, chargedNow[0])) return;
                if (useOnBlock(slot, face, DETONATE_HELD)) { detonatedNow[0] = true; onDetonated(spot, target); }
            });

            boolean replace = instant.getValue()
                    && workOwns
                    && !placedThisTick
                    && anchorAvailable()
                    && tick - lastPlaceTick >= placeDelay.getValue()
                    && instantReplaceSafe(spot, target);
            if (replace) {
                final int aSlot = findSlot(Blocks.RESPAWN_ANCHOR.asItem());
                steps.add(() -> { if (detonatedNow[0]) placeAnchorAt(aSlot, spot, true); });
                workPlaced = true;
                workCharged = false;
                workPlacedTick = tick;
                lastPlaceTick = tick;
            } else {
                workPlaced = false;
                workCharged = false;
            }
        }

        flush(event, steps, spot);
    }

    private void flush(EventInteract event, List<Runnable> steps, BlockPos spot) {
        if (steps.isEmpty()) return;
        Runnable seq = () -> {
            boolean wasSneaking = Mint.mc.player.isSneaking();
            if (wasSneaking) sendSneak(false);
            try {
                for (Runnable s : steps) s.run();
            } finally {
                if (wasSneaking) sendSneak(true);
            }
        };
        Vec3d aim = aimPoint.getValue() == AimPoint.Nearest
                ? MathHelper.closestPointToBox(EntityHelper.getEyePos(Mint.mc.player), new Box(spot))
                : Vec3d.ofCenter(spot);
        addInteraction(event, seq, aim);
    }

    private boolean workValid() {
        if (workTarget == null || !EntityHelper.isAlive(workTarget) || EntityHelper.getHealth(workTarget) <= 0) return false;
        return withinReach(workSpot, Math.max(range.getValue(), wallsRange.getValue()));
    }

    private boolean pickSpot() {
        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        final double exReach = Math.max(range.getValue(), wallsRange.getValue());
        final int r = (int) Math.ceil(anchorRange.getValue());

        BlockPos bestPos = null;
        LivingEntity bestTgt = null;
        BlockHitResult bestPlaceHit = null;
        int bestCharges = -1;
        double bestPick = -1;
        double curDmg = -1;
        double bestOtherDmg = -1;

        for (LivingEntity target : targets) {
            BlockPos center = target.getBlockPos();
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            for (int ox = -r; ox <= r; ox++) {
                for (int oy = -r; oy <= r; oy++) {
                    for (int oz = -r; oz <= r; oz++) {
                        BlockPos pos = center.add(ox, oy, oz);
                        Vec3d posCenter = Vec3d.ofCenter(pos);
                        if (posCenter.distanceTo(targetCenter) > 5.0) continue;   // out of meaningful blast range
                        if (posCenter.distanceTo(eye) > anchorRange.getValue() + 1.5) continue;
                        if (!withinReach(pos, exReach)) continue;

                        double self = selfDamage(pos);
                        if (!selfSafe(self)) continue;
                        double dmg = enemyDamage(target, pos);
                        if (!passesDamage(dmg, self, target, effMinDamage())) continue;

                        BlockState state = WorldHelper.getBlockState(pos);
                        boolean anchor = state.isOf(Blocks.RESPAWN_ANCHOR);
                        BlockHitResult placeHit = null;
                        int charges = -1;

                        if (anchor) {
                            charges = state.get(RespawnAnchorBlock.CHARGES);
                        } else {
                            if (!WorldHelper.isInWorldBounds(pos) || !WorldHelper.isRegionLoaded(pos)) continue;
                            if (!WorldHelper.isReplaceable(pos) || !WorldHelper.canPlaceAt(pos)) continue;
                            if (!WorldHelper.isValidPlacement(pos, Blocks.RESPAWN_ANCHOR)) continue;
                            if (!PlaceHelper.isEmpty(pos)) continue;
                            placeHit = PlaceHelper.cast(pos, airPlace.getValue(), antiCheat.getValue(),
                                    range.getValue(), wallsRange.getValue(), strictDirection.getValue());
                            if (placeHit == null) continue;
                        }

                        double pick = dmg;
                        if (anchor) pick += charges >= 1 ? CHARGED_ANCHOR_BONUS : EXISTING_ANCHOR_BONUS;
                        if (workSpot != null && pos.equals(workSpot)) curDmg = dmg;
                        else if (dmg > bestOtherDmg) bestOtherDmg = dmg;
                        if (pick <= bestPick) continue;
                        bestPick = pick; bestPos = pos; bestTgt = target;
                        bestPlaceHit = placeHit; bestCharges = charges;
                    }
                }
            }
        }

        if (curDmg >= 0 && bestOtherDmg <= curDmg + stickBonus.getValue()) {
            return true;
        }

        if (bestPos == null) return false;
        if (bestPlaceHit != null && !anchorAvailable()) return false;
        if (bestCharges < 1 && !glowstoneAvailable()) return false;
        if (computeAnchorFace(bestPos) == null) return false;

        if (!bestPos.equals(workSpot)) {
            workSpot = bestPos;
            workOwns = false;
            workPlaced = false;
            workCharged = false;
            workSeenTick = tick;
        }

        workTarget = bestTgt;
        return true;
    }

    private void onDetonated(BlockPos pos, LivingEntity target) {
        displayTarget = target;
        displayDamage = enemyDamage(target, pos);
        detonations.add(System.currentTimeMillis());
    }

    private boolean placeAnchor(int slot, BlockHitResult hit) {
        return performStep(slot, s -> s.isOf(Blocks.RESPAWN_ANCHOR.asItem()),
                () -> PlaceHelper.place(antiCheat.getValue(), hit, Hand.MAIN_HAND));
    }

    private boolean placeAnchorAt(int slot, BlockPos spot, boolean fake) {
        if (fake && fakeAir.getValue()) Mint.mc.world.setBlockState(spot, Blocks.AIR.getDefaultState());
        if (validate.getValue() && !WorldHelper.isReplaceable(spot)) return false;
        BlockHitResult hit = PlaceHelper.cast(spot, airPlace.getValue(), antiCheat.getValue(),
                range.getValue(), wallsRange.getValue(), strictDirection.getValue());
        if (hit == null) return false;
        if (validate.getValue() && !PlaceRenderer.getRenderPos(hit).equals(spot)) return false;
        boolean ok = placeAnchor(slot, hit);
        if (ok) render.render(hit);
        return ok;
    }

    private boolean canChargeNow(BlockPos spot, boolean placedThisSeq) {
        if (!validate.getValue()) return true;
        if (placedThisSeq) return true;
        BlockState s = WorldHelper.getBlockState(spot);
        return s.isOf(Blocks.RESPAWN_ANCHOR) && s.get(RespawnAnchorBlock.CHARGES) < 1;
    }

    private boolean canDetonateNow(BlockPos spot, boolean chargedThisSeq) {
        if (!validate.getValue()) return true;
        if (chargedThisSeq) return true;
        BlockState s = WorldHelper.getBlockState(spot);
        return s.isOf(Blocks.RESPAWN_ANCHOR) && s.get(RespawnAnchorBlock.CHARGES) >= 1;
    }

    private boolean instantReplaceSafe(BlockPos spot, LivingEntity target) {
        double self = selfDamage(spot);
        if (!selfSafe(self)) return false;
        double dmg = enemyDamage(target, spot);
        return passesDamage(dmg, self, target, effMinDamage());
    }

    private static final Predicate<ItemStack> DETONATE_HELD = s -> s.isEmpty() || s.getItem() != Items.GLOWSTONE;

    private boolean useOnBlock(int slot, BlockHitResult hit, Predicate<ItemStack> heldOk) {
        return performStep(slot, heldOk, () -> interactBlockRaw(hit));
    }

    private boolean performStep(int slot, Predicate<ItemStack> heldOk, Runnable action) {
        ToggleableSwapType mode = swapMode.getValue();

        if (mode == ToggleableSwapType.Off) {
            if (!heldOk.test(Mint.mc.player.getMainHandStack())) return false;
            action.run();
            return true;
        }

        if (slot < 0) return false;

        if (mode == ToggleableSwapType.Normal) {
            InvHelper.swapToSlot(slot, ToggleableSwapType.Normal);
            action.run();
            return true;
        }

        // Silent / Alt
        boolean swapped = InvHelper.swapToSlot(slot, mode);
        if (!swapped) {
            if (!heldOk.test(Mint.mc.player.getMainHandStack())) return false;
            if (Mint.mc.getNetworkHandler() != null) {
                Mint.mc.getNetworkHandler().sendPacket(
                        new UpdateSelectedSlotC2SPacket(Mint.mc.player.getInventory().getSelectedSlot()));
            }
        }
        try {
            action.run();
        } finally {
            if (swapped) InvHelper.swapBack();
        }
        return true;
    }

    private void interactBlockRaw(BlockHitResult hit) {
        try {
            sendInteract(hit);
        } catch (Throwable t) {
            Mint.mc.interactionManager.interactBlock(Mint.mc.player, Hand.MAIN_HAND, hit);
        }
        Mint.mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void sendInteract(BlockHitResult hit) {
        if (Mint.mc.getNetworkHandler() == null || Mint.mc.world == null) return;
        try (PendingUpdateManager pum = Mint.mc.world.getPendingUpdateManager().incrementSequence()) {
            Mint.mc.getNetworkHandler().sendPacket(
                    new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, pum.getSequence()));
        }
    }

    private void sendSneak(boolean sneaking) {
        if (Mint.mc.getNetworkHandler() == null) return;
        PlayerInput cur = Mint.mc.player.input.playerInput;
        PlayerInput modified = new PlayerInput(cur.forward(), cur.backward(), cur.left(), cur.right(), cur.jump(), sneaking, cur.sprint());
        Mint.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(modified));
    }

    @EventHandler
    private void onBind(EventBind event) {
        if (override.getBind() == -1) return;
        if (event.bind == override.getBind() && event.isButton == override.isButton()) {
            overrideHeld = event.action != 0;
        }
    }

    private double effMinDamage() {
        return overrideHeld ? overrideMinDmg.getValue() : minDamage.getValue();
    }

    private void addInteraction(EventInteract event, Runnable action, Vec3d aim) {
        if (rotate.getValue()) {
            float[] rot = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), aim);
            event.addInteraction(new Interaction(action, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(action));
        }
    }

    // damage helpers
    private double enemyDamage(LivingEntity target, BlockPos pos) {
        return DamageUtils.anchorDamage(target, Vec3d.ofCenter(pos), predict.getValue(), assumeMaxArmor.getValue());
    }

    private double selfDamage(BlockPos pos) {
        return DamageUtils.anchorDamage(Mint.mc.player, Vec3d.ofCenter(pos), predict.getValue());
    }

    private boolean selfSafe(double self) {
        if (self > maxSelfDamage.getValue()) return false;
        return !antiSuicide.getValue() || EntityHelper.getHealth(Mint.mc.player) - self > minHealth.getValue();
    }

    private boolean passesDamage(double dmg, double self, LivingEntity target, double minDmg) {
        if (balance.getValue() > 0 && self > 0 && dmg / self < balance.getValue()) return false;
        if (lethalHP.getValue() > 0 && EntityHelper.getHealth(target) <= lethalHP.getValue()) return dmg > 0;
        return dmg >= minDmg;
    }

    //inventory helpers
    private int findSlot(net.minecraft.item.Item item) {
        return swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(item) : InvHelper.findInHotbar(item);
    }

    private boolean anchorAvailable() {
        if (swapMode.getValue() == ToggleableSwapType.Off) return holding(Blocks.RESPAWN_ANCHOR.asItem());
        return findSlot(Blocks.RESPAWN_ANCHOR.asItem()) != -1;
    }

    private boolean glowstoneAvailable() {
        if (swapMode.getValue() == ToggleableSwapType.Off) return holding(Items.GLOWSTONE);
        return findSlot(Items.GLOWSTONE) != -1;
    }

    private boolean detonateAvailable() {
        if (swapMode.getValue() == ToggleableSwapType.Off) {
            ItemStack main = Mint.mc.player.getMainHandStack();
            return main.isEmpty() || main.getItem() != Items.GLOWSTONE;
        }
        return findDetonateSlot() != Integer.MIN_VALUE;
    }

    private int findDetonateSlot() {
        Predicate<ItemStack> good = s -> !s.isEmpty() && s.getItem() != Items.GLOWSTONE && !(s.getItem() instanceof BlockItem);
        int slot = swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(good) : InvHelper.findInHotbar(good);
        if (slot != -1) return slot;
        int empty = InvHelper.findInHotbar(ItemStack::isEmpty);
        if (empty != -1) return empty;
        Predicate<ItemStack> nonGlow = s -> !s.isEmpty() && s.getItem() != Items.GLOWSTONE;
        int any = swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(nonGlow) : InvHelper.findInHotbar(nonGlow);
        return any != -1 ? any : Integer.MIN_VALUE;
    }

    private boolean holding(net.minecraft.item.Item item) {
        return Mint.mc.player.getMainHandStack().getItem() == item
                || Mint.mc.player.getOffHandStack().getItem() == item;
    }

    private BlockHitResult computeAnchorFace(BlockPos pos) {
        Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        boolean grim = antiCheat.getValue() == InteractionMode.Grim;
        double reach = Math.max(range.getValue(), wallsRange.getValue());
        Vec3d center = Vec3d.ofCenter(pos);

        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        BlockHitResult fallback = null;
        double fallbackDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            Vec3d normal = new Vec3d(dir.getUnitVector());
            Vec3d face = center.add(normal.multiply(0.5));
            if (eye.subtract(face).dotProduct(normal) <= 0) continue; // back-face cull

            double dist = face.distanceTo(eye);
            if (!grim && dist > reach) continue;

            if (dist < fallbackDist) {
                fallbackDist = dist;
                fallback = new BlockHitResult(face, dir, pos, false);
            }

            HitResult rc = WorldHelper.raycast(eye, face);
            if (rc instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK && !bhr.getBlockPos().equals(pos)) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = new BlockHitResult(face, dir, pos, false);
            }
        }
        return best != null ? best : fallback;
    }

    private boolean withinReach(BlockPos pos, double range) {
        if (antiCheat.getValue() == InteractionMode.Grim) return true;
        return Vec3d.ofCenter(pos).distanceTo(EntityHelper.getEyePos(Mint.mc.player)) <= range + 0.5;
    }

    private void updateTargets() {
        targets.clear();
        Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);

        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : Mint.mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity le)) continue;
            if (le == Mint.mc.player) continue;
            if (EntityHelper.isFriend(le)) continue;
            if (!EntityHelper.isAlive(le)) continue;
            if (EntityHelper.getHealth(le) <= 0) continue;
            if (Vec3d.ofCenter(le.getBlockPos()).distanceTo(eye) > targetRange.getValue()
                    && le.distanceTo(Mint.mc.player) > targetRange.getValue()) continue;
            found.add(le);
        }

        found.sort(targetComparator());
        for (LivingEntity le : found) {
            if (targets.size() >= maxTargets.getValue().intValue()) break;
            targets.add(le);
        }

        if (!targets.isEmpty() && (displayTarget == null || !targets.contains(displayTarget))) {
            displayTarget = targets.get(0);
        }
    }

    private Comparator<LivingEntity> targetComparator() {
        return switch (targetMode.getValue()) {
            case Furthest -> Comparator.comparingDouble((LivingEntity e) -> e.distanceTo(Mint.mc.player)).reversed();
            case LowestHealth -> Comparator.comparingDouble(EntityHelper::getHealth);
            case LowestArmor -> Comparator.comparingInt(EntityHelper::getArmorDurability);
            case Closest -> Comparator.comparingDouble(e -> e.distanceTo(Mint.mc.player));
        };
    }

    private void updateCps() {
        long now = System.currentTimeMillis();
        while (!detonations.isEmpty() && now - detonations.peekFirst() > 1000L) detonations.pollFirst();
    }

    @Override
    public String getArrayListInfo() {
        if (Mint.mc.player == null) return "";
        StringBuilder sb = new StringBuilder();

        if (showTarget.getValue() && displayTarget != null) {
            sb.append(displayTarget.getName().getString());
        }
        if (showDamage.getValue() && displayTarget != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(String.format("%.1f", displayDamage));
        }
        if (showCPS.getValue()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(detonations.size()).append(" cps");
        }
        return sb.toString();
    }
}
