package me.otter.mint.client.impl.modules;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventBind;
import dev.boze.api.event.EventInteract;
import dev.boze.api.event.EventPlayerUpdate;
import dev.boze.api.option.*;
import dev.boze.api.utility.EntityHelper;
import dev.boze.api.utility.MathHelper;
import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.InteractionMode;
import dev.boze.api.utility.interaction.Interaction;
import dev.boze.api.utility.interaction.InvHelper;
import dev.boze.api.utility.interaction.PlaceHelper;
import dev.boze.api.utility.interaction.ToggleableSwapType;
import me.otter.mint.Mint;
import me.otter.mint.client.core.utils.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BedItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public class BedAuraModule extends AddonModule {

    private enum TargetMode { Closest, Furthest, LowestHealth, LowestArmor }
    private enum AimPoint { Center, Nearest }
    private enum PlacePriority { Closest, Down, Hole }

    // General
    private final ModeOption<InteractionMode> antiCheat = new ModeOption<>(this, "AntiCheat", "Interaction mode.", InteractionMode.NCP);
    private final SliderOption range = new SliderOption(this, "Range", "Reach for placing/breaking beds.", 4.5, 1.0, 6.0, 0.1);
    private final SliderOption wallsRange = new SliderOption(this, "WallsRange", "Reach through walls (NCP only).", 4.0, 0.0, 6.0, 0.1);
    private final ModeOption<ToggleableSwapType> swapMode = new ModeOption<>(this, "SwapMode", "How to swap items into hand.", ToggleableSwapType.Silent);
    private final ToggleOption multiTask = new ToggleOption(this, "MultiTask", "Run while using items.", false);
    private final ToggleOption pauseOnUse = new ToggleOption(this, "PauseOnUse", "Pause while using an item.", true);

    // Rotate
    private final ToggleOption rotate = new ToggleOption(this, "Rotate", "Rotate to the bed before placing/breaking.", true);
    private final ToggleOption strictDirection = new ToggleOption(this, "StrictDirection", "Pass strict direction checks for placements.", true, rotate::getValue, rotate);
    private final ModeOption<AimPoint> aimPoint = new ModeOption<>(this, "AimPoint", "Where on the bed to aim when breaking.", AimPoint.Center, rotate::getValue, rotate);

    // Targeting
    private final PageOption targeting = new PageOption(this, "Targeting", "Target selection.");
    private final ModeOption<TargetMode> targetMode = new ModeOption<>(this, "Mode", "Algorithm for selecting targets.", TargetMode.Closest, targeting);
    private final SliderOption targetRange = new SliderOption(this, "TargetRange", "Range within which to select targets.", 8.0, 1.0, 16.0, 0.5, targeting);
    private final SliderOption bedRange = new SliderOption(this, "BedRange", "Max distance from a target to place beds.", 4.0, 1.0, 8.0, 0.5, targeting);
    private final SliderOption maxTargets = new SliderOption(this, "MaxTargets", "Max targets to consider.", 3.0, 1.0, 8.0, 1.0, targeting);

    // Behavior
    private final PageOption behavior = new PageOption(this, "Behavior", "How to bed.");
    private final SliderOption placeDelay = new SliderOption(this, "PlaceDelay", "Ticks between placements.", 0.0, 0.0, 10.0, 1.0, behavior);
    private final SliderOption breakDelay = new SliderOption(this, "BreakDelay", "Ticks between breaks (detonations).", 0.0, 0.0, 10.0, 1.0, behavior);
    private final SliderOption await = new SliderOption(this, "Await", "Ticks to wait for a placed/broken bed to update before acting on that spot again.", 6.0, 0.0, 20.0, 1.0, behavior);
    private final ToggleOption instant = new ToggleOption(this, "Instant", "Re-place a fresh bed the same tick the owned bed detonates.", true, behavior);
    private final ToggleOption intoTarget = new ToggleOption(this, "IntoTarget", "Extend the bed's second block into the target for max damage (place inside entities).", true, behavior);
    private final ToggleOption airPlace = new ToggleOption(this, "AirPlace", "Allow placing without a supporting block.", false, behavior);
    private final ToggleOption doubleInteract = new ToggleOption(this, "DoubleInteract", "Interact both bed halves when breaking.", true, behavior);
    private final ToggleOption validate = new ToggleOption(this, "Validate", "Verify live block state before each place/break.", true, behavior);

    // Damage / Safety
    private final PageOption damage = new PageOption(this, "Damage", "Damage thresholds and self safety.");
    private final SliderOption minDamage = new SliderOption(this, "MinDamage", "Minimum damage to place/break a bed.", 6.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption lethalHP = new SliderOption(this, "LethalHP", "Ignore min damage when target health (+absorption) is at/below this (face-place).", 0.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption maxSelfDamage = new SliderOption(this, "MaxSelfDamage", "Maximum self damage allowed.", 8.0, 0.0, 36.0, 0.5, damage);
    private final SliderOption minHealth = new SliderOption(this, "MinHealth", "Don't act while your health (+absorption) is at/below this.", 4.0, 0.0, 36.0, 0.5, damage);
    private final ToggleOption antiSuicide = new ToggleOption(this, "AntiSuicide", "Never act if the predicted self damage would drop you to/below MinHealth.", true, damage);
    private final SliderOption balance = new SliderOption(this, "Balance", "Minimum enemy/self damage ratio (0 = off).", 0.0, 0.0, 10.0, 0.1, damage);
    private final ToggleOption assumeMaxArmor = new ToggleOption(this, "AssumeMaxArmor", "Assume targets have a full max-enchant armor set.", false, damage);
    private final ToggleOption predict = new ToggleOption(this, "Predict", "Extrapolate target movement for damage checks.", true, damage);
    private final BindOption override = new BindOption(this, "Override", "Hold to use OverrideMinDmg instead of MinDamage (more aggressive).", -1, false, damage);
    private final SliderOption overrideMinDmg = new SliderOption(this, "OverrideMinDmg", "MinDamage used while Override is held.", 1.0, 0.0, 36.0, 0.5, damage);

    // AutoCraft
    private final PageOption autoCraftPage = new PageOption(this, "AutoCraft", "Resupply beds from a crafting table.");
    private final ToggleOption autoCraft = new ToggleOption(this, "AutoCraft", "Automatically craft beds when low.", false, autoCraftPage);
    private final SliderOption minBeds = new SliderOption(this, "MinBeds", "Craft when bed count is at/below this.", 4.0, 0.0, 36.0, 1.0, autoCraft::getValue, autoCraftPage);
    private final SliderOption bedsPerCraft = new SliderOption(this, "BedsPerCraft", "Max beds to craft per session.", 8.0, 1.0, 27.0, 1.0, autoCraft::getValue, autoCraftPage);
    private final SliderOption minFreeSlots = new SliderOption(this, "MinFreeSlots", "Keep this many inventory slots free so returned wool/planks aren't dropped.", 6.0, 0.0, 27.0, 1.0, autoCraft::getValue, autoCraftPage);
    private final ToggleOption autoPlaceTable = new ToggleOption(this, "AutoPlaceTable", "Place a crafting table if none is nearby.", true, autoCraft::getValue, autoCraftPage);
    private final ModeOption<PlacePriority> tablePriority = new ModeOption<>(this, "TablePriority", "Where to place the crafting table. Closest: nearest viable spot. Down: prefer placing low. Hole: prefer holes.", PlacePriority.Closest, () -> autoCraft.getValue() && autoPlaceTable.getValue(), autoCraftPage);
    private final SliderOption tableDownRange = new SliderOption(this, "TableDownRange", "How far below to favour table spots in Down mode.", 4.0, 0.0, 12.0, 0.5, () -> autoCraft.getValue() && autoPlaceTable.getValue() && tablePriority.getValue() == PlacePriority.Down, autoCraftPage);
    private final SliderOption craftDelay = new SliderOption(this, "CraftDelay", "Ticks between craft actions.", 4.0, 0.0, 40.0, 1.0, autoCraft::getValue, autoCraftPage);
    private final SliderOption tableRange = new SliderOption(this, "TableRange", "Range to search for / place a crafting table.", 4.0, 1.0, 6.0, 0.5, autoCraft::getValue, autoCraftPage);

    // List
    private final PageOption list = new PageOption(this, "List", "ArrayList HUD info.");
    private final ToggleOption showTarget = new ToggleOption(this, "Target", "Show target name in ArrayList.", true, list);
    private final ToggleOption showDamage = new ToggleOption(this, "Damage", "Show target damage in ArrayList.", true, list);
    private final ToggleOption showCPS = new ToggleOption(this, "CPS", "Show detonations-per-second in ArrayList.", false, list);

    private static final Predicate<ItemStack> NOT_BED = s -> !(s.getItem() instanceof BedItem);

    private final List<LivingEntity> targets = new ArrayList<>();
    private final Deque<Long> detonations = new ArrayDeque<>();

    private long tick = 0;
    private long lastPlaceTick = -1000L;
    private long lastBreakTick = -1000L;
    private long nextAcquireTick = 0L;
    private boolean overrideHeld = false;

    private BlockPos lockFoot = null;
    private BlockPos lockHead = null;
    private Direction lockDir = null;
    private LivingEntity lockTarget = null;
    private boolean lockAdopt = false;
    private boolean lockExpectBed = false;
    private long lockActionTick = -1000L;

    private LivingEntity displayTarget = null;
    private double displayDamage = 0;

    private long lastCraftMs = 0L;

    public BedAuraModule() {
        super("BedAura", "Automatically places and detonates beds to nuke enemies in the Nether/End.");
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
        clearLock();
        displayTarget = null;
        displayDamage = 0;
        lastPlaceTick = -1000L;
        lastBreakTick = -1000L;
        nextAcquireTick = 0L;
    }

    private void clearLock() {
        lockFoot = null;
        lockHead = null;
        lockDir = null;
        lockTarget = null;
        lockAdopt = false;
        lockExpectBed = false;
        lockActionTick = -1000L;
    }

    @EventHandler
    private void onInteract(EventInteract event) {
        if (Mint.mc.player == null || Mint.mc.world == null) return;
        if (event.getMode() != antiCheat.getValue()) return;
        if (Mint.mc.world.getRegistryKey() == World.OVERWORLD) return; // beds only explode in the Nether/End
        if (Mint.mc.player.currentScreenHandler instanceof CraftingScreenHandler) return; // crafting; pause combat
        if (!multiTask.getValue() && pauseOnUse.getValue() && Mint.mc.player.isUsingItem()) return;
        if (EntityHelper.getHealth(Mint.mc.player) <= minHealth.getValue()) return;

        tick++;
        updateCps();
        updateTargets();
        if (targets.isEmpty()) { clearLock(); return; }

        // keep the current spot until it is no longer viable
        if (!lockValid()) {
            if (tick < nextAcquireTick) return;
            if (!acquireLock()) { nextAcquireTick = tick + 3; clearLock(); return; }
        }

        BlockPos bed = bedAtLock();
        if (bed != null) {
            // break the bed sitting on our locked spot
            if (tick - lastBreakTick < breakDelay.getValue()) return;
            if (!lockExpectBed && tick - lockActionTick < awaitTicks()) return; // just broke; wait for removal
            detonateLock(event, bed);
        } else {
            // no bed yet -> place one
            if (lockAdopt) return;
            if (tick - lastPlaceTick < placeDelay.getValue()) return;
            if (lockExpectBed && tick - lockActionTick < awaitTicks()) return;
            if (!bedAvailable()) return;
            placeLock(event);
        }
    }

    private long awaitTicks() {
        return await.getValue().longValue();
    }

    private double exReach() {
        return Math.max(range.getValue(), wallsRange.getValue());
    }

    private boolean lockValid() {
        if (lockFoot == null || lockTarget == null) return false;
        if (!EntityHelper.isAlive(lockTarget) || EntityHelper.getHealth(lockTarget) <= 0) return false;
        if (EntityHelper.isFriend(lockTarget)) return false;

        Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        if (Vec3d.ofCenter(lockTarget.getBlockPos()).distanceTo(eye) > targetRange.getValue() + 1.0
                && lockTarget.distanceTo(Mint.mc.player) > targetRange.getValue() + 1.0) return false;

        if (lockAdopt) return bedAtLock() != null; // valid while a reachable, adoptable bed remains

        boolean reach = withinReach(lockFoot, exReach()) || (lockHead != null && withinReach(lockHead, exReach()));
        if (!reach) return false;

        double dFoot = enemyDamage(lockTarget, lockFoot);
        double dHead = lockHead != null ? enemyDamage(lockTarget, lockHead) : -1;
        BlockPos det = dHead >= dFoot ? lockHead : lockFoot;
        double dmg = Math.max(dFoot, dHead);
        double self = selfDamage(det);
        if (!selfSafe(self)) return false;
        return passesDamage(dmg, self, lockTarget, effMinDamage());
    }

    private boolean acquireLock() {
        // prefer adopting an existing reachable bed that already damages a target
        ExistingBed eb = findExistingBed();
        if (eb != null) {
            lockFoot = eb.pos;
            lockHead = otherBedHalf(eb.pos);
            lockDir = null;
            lockTarget = eb.target;
            lockAdopt = true;
            lockExpectBed = false;
            lockActionTick = -1000L;
            return true;
        }
        PlaceData best = findPlace();
        if (best == null) return false;
        lockFoot = best.foot;
        lockDir = best.dir;
        lockHead = best.foot.offset(best.dir);
        lockTarget = best.target;
        lockAdopt = false;
        lockExpectBed = false;
        lockActionTick = -1000L;
        return true;
    }

    private BlockPos otherBedHalf(BlockPos bedPos) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (isBedBlock(bedPos.offset(d))) return bedPos.offset(d);
        }
        return null;
    }

    // best reachable bed half on the locked spot to detonate (max target damage), or null
    private BlockPos bedAtLock() {
        if (lockFoot == null || lockTarget == null) return null;
        BlockPos best = null;
        double bestDmg = -1;
        for (BlockPos p : new BlockPos[]{lockFoot, lockHead, lockAdopt ? otherBedHalf(lockFoot) : null}) {
            if (p == null || !isBedBlock(p)) continue;
            if (!withinReach(p, exReach())) continue;
            double d = enemyDamage(lockTarget, p);
            if (d > bestDmg) { bestDmg = d; best = p; }
        }
        return best;
    }

    private ExistingBed findExistingBed() {
        final double exR = exReach();
        final int r = (int) Math.ceil(bedRange.getValue());

        ExistingBed best = null;
        for (LivingEntity target : targets) {
            BlockPos center = target.getBlockPos();
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            for (int ox = -r; ox <= r; ox++) {
                for (int oy = -r; oy <= r; oy++) {
                    for (int oz = -r; oz <= r; oz++) {
                        BlockPos pos = center.add(ox, oy, oz);
                        if (!isBedBlock(pos)) continue;
                        if (Vec3d.ofCenter(pos).distanceTo(targetCenter) > 6.0) continue;
                        if (!withinReach(pos, exR)) continue;

                        double self = selfDamage(pos);
                        if (!selfSafe(self)) continue;
                        double dmg = enemyDamage(target, pos);
                        if (!passesDamage(dmg, self, target, effMinDamage())) continue;

                        if (best == null || dmg > best.damage) best = new ExistingBed(pos, target, dmg);
                    }
                }
            }
        }
        return best;
    }

    private PlaceData findPlace() {
        final double exReach = Math.max(range.getValue(), wallsRange.getValue());
        final int r = (int) Math.ceil(bedRange.getValue());
        final Direction only = rotate.getValue() ? null : Direction.fromHorizontalDegrees(Mint.mc.player.getYaw());

        PlaceData best = null;
        for (LivingEntity target : targets) {
            BlockPos center = target.getBlockPos();
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            for (int ox = -r; ox <= r; ox++) {
                for (int oy = -r; oy <= r; oy++) {
                    for (int oz = -r; oz <= r; oz++) {
                        BlockPos foot = center.add(ox, oy, oz);
                        if (!footPlaceable(foot, exReach)) continue;

                        for (Direction dir : Direction.Type.HORIZONTAL) {
                            if (only != null && dir != only) continue;
                            BlockPos head = foot.offset(dir);
                            if (!headPlaceable(head)) continue;

                            // bed explodes wherever you interact it; detonate the half that hurts the target most
                            double dFoot = enemyDamage(target, foot);
                            double dHead = enemyDamage(target, head);
                            boolean useHead = dHead >= dFoot;
                            BlockPos detonate = useHead ? head : foot;
                            double dmg = Math.max(dFoot, dHead);

                            double self = selfDamage(detonate);
                            if (!selfSafe(self)) continue;
                            if (!passesDamage(dmg, self, target, effMinDamage())) continue;

                            // bias toward driving the head into the target's space
                            double score = dmg;
                            if (Vec3d.ofCenter(head).distanceTo(targetCenter) < Vec3d.ofCenter(foot).distanceTo(targetCenter))
                                score += 0.01;

                            if (best == null || score > best.score)
                                best = new PlaceData(foot, dir, detonate, target, dmg, score);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean footPlaceable(BlockPos foot, double reach) {
        if (!withinReach(foot, reach)) return false;
        if (!WorldHelper.isInWorldBounds(foot) || !WorldHelper.isRegionLoaded(foot)) return false;
        if (!WorldHelper.isReplaceable(foot)) return false;
        if (!WorldHelper.canPlaceAt(foot)) return false;
        if (!WorldHelper.isValidPlacement(foot, Blocks.WHITE_BED)) return false;
        if (!PlaceHelper.isEmpty(foot)) return false;
        if (!airPlace.getValue() && WorldHelper.isReplaceable(foot.down())) return false; // need a floor
        return true;
    }

    private boolean headPlaceable(BlockPos head) {
        if (!WorldHelper.isReplaceable(head)) return false;
        if (intoTarget.getValue()) return true; // allow the second block inside the target entity
        if (!PlaceHelper.isEmpty(head)) return false;
        return !entityAt(head);
    }

    private void detonateLock(EventInteract event, BlockPos bedPos) {
        final BlockHitResult face = computeBedFace(bedPos);
        if (face == null) return;
        final int slot = findDetonateSlot();
        final LivingEntity target = lockTarget;
        final boolean[] detonated = {false};

        Runnable action = () -> {
            if (validate.getValue() && !isBedBlock(bedPos)) return;
            if (useOnBlock(slot, face, NOT_BED)) {
                detonated[0] = true;
                if (doubleInteract.getValue()) interactOtherHalf(bedPos, slot);
                onDetonated(bedPos, target);
            }
        };
        lastBreakTick = tick;
        lockActionTick = tick;
        lockExpectBed = false;
        displayTarget = target;
        submit(event, action, aimVec(bedPos));

        // instant: re-place a fresh bed on the same spot the same tick (our own locks only)
        if (!lockAdopt) queueInstantReplace(event, detonated, target);
    }

    private void queueInstantReplace(EventInteract event, boolean[] detonated, LivingEntity target) {
        if (!instant.getValue()) return;
        if (lockFoot == null || lockDir == null) return;
        if (!bedAvailable()) return;
        if (tick - lastPlaceTick < placeDelay.getValue()) return;
        if (!instantReplaceSafe(lockFoot, lockDir, target)) return;

        final BlockHitResult hit = computePlaceHit(lockFoot, lockDir);
        if (hit == null) return;
        final int slot = findBedSlot();

        // skip the clientside replaceable check: the detonate packet is sent first, so the
        // explosion clears both bed blocks server-side before this placement is processed
        Runnable action = () -> {
            if (!detonated[0]) return;
            placeBed(slot, hit);
        };

        lastPlaceTick = tick;
        lockActionTick = tick;
        lockExpectBed = true;

        float yaw = lockDir.getPositiveHorizontalDegrees();
        float pitch = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), hit.getPos())[1];
        submitRotated(event, action, yaw, pitch);
    }

    private boolean instantReplaceSafe(BlockPos foot, Direction dir, LivingEntity target) {
        BlockPos head = foot.offset(dir);
        double dFoot = enemyDamage(target, foot);
        double dHead = enemyDamage(target, head);
        BlockPos detonate = dHead >= dFoot ? head : foot;
        double dmg = Math.max(dFoot, dHead);
        double self = selfDamage(detonate);
        if (!selfSafe(self)) return false;
        return passesDamage(dmg, self, target, effMinDamage());
    }

    private void placeLock(EventInteract event) {
        final BlockPos foot = lockFoot;
        final Direction dir = lockDir;
        if (dir == null) return;
        final BlockHitResult hit = computePlaceHit(foot, dir);
        if (hit == null) { clearLock(); return; }
        final int slot = findBedSlot();

        Runnable action = () -> {
            if (validate.getValue() && (!WorldHelper.isReplaceable(foot) || !WorldHelper.isReplaceable(foot.offset(dir)))) return;
            placeBed(slot, hit);
        };

        lastPlaceTick = tick;
        lockActionTick = tick;
        lockExpectBed = true;
        displayTarget = lockTarget;

        // a bed faces the player's yaw; set yaw so the head lands at foot.offset(dir) (into the target)
        float yaw = dir.getPositiveHorizontalDegrees();
        float pitch = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), hit.getPos())[1];
        submitRotated(event, action, yaw, pitch);
    }

    private void onDetonated(BlockPos pos, LivingEntity target) {
        displayTarget = target;
        displayDamage = enemyDamage(target, pos);
        detonations.add(System.currentTimeMillis());
    }

    private void interactOtherHalf(BlockPos bedPos, int slot) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos other = bedPos.offset(dir);
            if (isBedBlock(other) && withinReach(other, Math.max(range.getValue(), wallsRange.getValue()))) {
                BlockHitResult face = computeBedFace(other);
                if (face != null) useOnBlock(slot, face, NOT_BED);
                return;
            }
        }
    }

    private boolean isBedBlock(BlockPos pos) {
        return WorldHelper.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean entityAt(BlockPos pos) {
        Box box = new Box(pos);
        for (Entity e : Mint.mc.world.getEntities()) {
            if (e == Mint.mc.player) continue;
            if (e instanceof ItemEntity) continue;
            if (!e.isAlive()) continue;
            if (e.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private BlockHitResult computePlaceHit(BlockPos foot, Direction dir) {
        if (airPlace.getValue()) {
            return PlaceHelper.cast(foot, true, antiCheat.getValue(), range.getValue(), wallsRange.getValue(), strictDirection.getValue());
        }
        BlockPos support = foot.down();
        if (WorldHelper.isReplaceable(support)) return null;
        if (!withinReach(foot, Math.max(range.getValue(), wallsRange.getValue()))) return null;
        Vec3d hitVec = new Vec3d(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
        return new BlockHitResult(hitVec, Direction.UP, support, false);
    }

    private boolean placeBed(int slot, BlockHitResult hit) {
        return performStep(slot, s -> s.getItem() instanceof BedItem,
                () -> PlaceHelper.place(antiCheat.getValue(), hit, Hand.MAIN_HAND));
    }

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

    private Runnable wrap(Runnable action) {
        return () -> {
            boolean wasSneaking = Mint.mc.player.isSneaking();
            if (wasSneaking) sendSneak(false);
            try {
                action.run();
            } finally {
                if (wasSneaking) sendSneak(true);
            }
        };
    }

    private void submit(EventInteract event, Runnable action, Vec3d aim) {
        Runnable seq = wrap(action);
        if (rotate.getValue()) {
            float[] rot = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), aim);
            event.addInteraction(new Interaction(seq, rot[0], rot[1]));
        } else {
            event.addInteraction(new Interaction(seq));
        }
    }

    private void submitRotated(EventInteract event, Runnable action, float yaw, float pitch) {
        Runnable seq = wrap(action);
        if (rotate.getValue()) {
            event.addInteraction(new Interaction(seq, yaw, pitch));
        } else {
            event.addInteraction(new Interaction(seq));
        }
    }

    private Vec3d aimVec(BlockPos pos) {
        return aimPoint.getValue() == AimPoint.Nearest
                ? MathHelper.closestPointToBox(EntityHelper.getEyePos(Mint.mc.player), new Box(pos))
                : Vec3d.ofCenter(pos);
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

    private double enemyDamage(LivingEntity target, BlockPos pos) {
        return DamageUtils.explosionDamage(target, Vec3d.ofCenter(pos), 10f, predict.getValue(), DamageUtils.HIT_FACTORY, assumeMaxArmor.getValue());
    }

    private double selfDamage(BlockPos pos) {
        return DamageUtils.explosionDamage(Mint.mc.player, Vec3d.ofCenter(pos), 10f, predict.getValue(), DamageUtils.HIT_FACTORY, false);
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

    private int findBedSlot() {
        Predicate<ItemStack> bed = s -> s.getItem() instanceof BedItem;
        return swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(bed) : InvHelper.findInHotbar(bed);
    }

    private boolean bedAvailable() {
        if (swapMode.getValue() == ToggleableSwapType.Off) {
            return Mint.mc.player.getMainHandStack().getItem() instanceof BedItem
                    || Mint.mc.player.getOffHandStack().getItem() instanceof BedItem;
        }
        return findBedSlot() != -1;
    }

    private int findDetonateSlot() {
        Predicate<ItemStack> good = s -> !s.isEmpty() && !(s.getItem() instanceof BedItem) && !(s.getItem() instanceof BlockItem);
        int slot = swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(good) : InvHelper.findInHotbar(good);
        if (slot != -1) return slot;
        int empty = InvHelper.findInHotbar(ItemStack::isEmpty);
        if (empty != -1) return empty;
        int any = swapMode.getValue() == ToggleableSwapType.Alt ? InvHelper.find(NOT_BED) : InvHelper.findInHotbar(NOT_BED);
        return any != -1 ? any : Integer.MIN_VALUE;
    }

    private BlockHitResult computeBedFace(BlockPos pos) {
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

    private boolean withinReach(BlockPos pos, double reach) {
        if (antiCheat.getValue() == InteractionMode.Grim) return true;
        return Vec3d.ofCenter(pos).distanceTo(EntityHelper.getEyePos(Mint.mc.player)) <= reach + 0.5;
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

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate event) {
        if (!autoCraft.getValue()) return;
        if (Mint.mc.player == null || Mint.mc.world == null || Mint.mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (now - lastCraftMs < craftDelay.getValue() * 50L) return;

        // already in a crafting table -> fill, craft, close
        if (Mint.mc.player.currentScreenHandler instanceof CraftingScreenHandler csh) {
            if (countBeds() <= minBeds.getValue()) craftBeds(csh);
            Mint.mc.player.closeHandledScreen();
            lastCraftMs = now;
            return;
        }

        if (countBeds() > minBeds.getValue()) return;
        if (!hasIngredients()) return; // don't open a table we can't craft from
        if (freeInventorySlots() <= minFreeSlots.getValue()) return; // keep room for returned wool/planks

        BlockPos table = findCraftingTable();
        if (table == null) {
            if (autoPlaceTable.getValue() && tablePlaceAvailable()) {
                tryPlaceTable();
                lastCraftMs = now;
            }
            return;
        }

        openTable(table);
        lastCraftMs = now;
    }

    private int countBeds() {
        int count = 0;
        PlayerInventory inv = Mint.mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof BedItem) count += stack.getCount();
        }
        return count;
    }

    private int freeInventorySlots() {
        int free = 0;
        PlayerInventory inv = Mint.mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) free++;
        }
        return free;
    }

    // need a single stack of >=3 same-colour wool and at least 3 planks to craft a bed
    private boolean hasIngredients() {
        PlayerInventory inv = Mint.mc.player.getInventory();
        boolean wool = false;
        int planks = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isIn(ItemTags.WOOL) && s.getCount() >= 3) wool = true;
            if (s.isIn(ItemTags.PLANKS)) planks += s.getCount();
        }
        return wool && planks >= 3;
    }

    private BlockPos findCraftingTable() {
        Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        int r = (int) Math.ceil(tableRange.getValue());
        BlockPos base = Mint.mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int ox = -r; ox <= r; ox++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int oz = -r; oz <= r; oz++) {
                    BlockPos pos = base.add(ox, oy, oz);
                    if (!(WorldHelper.getBlockState(pos).getBlock() instanceof CraftingTableBlock)) continue;
                    double d = Vec3d.ofCenter(pos).distanceTo(eye);
                    if (d > tableRange.getValue() + 0.5) continue;
                    if (d < bestDist) { bestDist = d; best = pos; }
                }
            }
        }
        return best;
    }

    private void openTable(BlockPos table) {
        BlockHitResult face = computeBedFace(table);
        if (face == null) return;
        boolean wasSneaking = Mint.mc.player.isSneaking();
        if (wasSneaking) sendSneak(false);
        float[] rot = MathHelper.calculateRotation(EntityHelper.getEyePos(Mint.mc.player), face.getPos());
        Mint.mc.player.setYaw(rot[0]);
        Mint.mc.player.setPitch(rot[1]);
        Mint.mc.interactionManager.interactBlock(Mint.mc.player, Hand.MAIN_HAND, face);
        Mint.mc.player.swingHand(Hand.MAIN_HAND);
        if (wasSneaking) sendSneak(true);
    }

    private boolean tablePlaceAvailable() {
        return InvHelper.findInHotbar(Blocks.CRAFTING_TABLE) != -1 || InvHelper.find(Blocks.CRAFTING_TABLE) != -1;
    }

    private void tryPlaceTable() {
        BlockPos spot = pickTableSpot();
        if (spot == null) return;

        int slot = swapMode.getValue() == ToggleableSwapType.Alt
                ? InvHelper.find(Blocks.CRAFTING_TABLE) : InvHelper.findInHotbar(Blocks.CRAFTING_TABLE);
        if (slot == -1) return;

        Vec3d hitVec = new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, spot.down(), false);
        performStep(slot, s -> s.getItem() == Blocks.CRAFTING_TABLE.asItem(),
                () -> PlaceHelper.place(antiCheat.getValue(), hit, Hand.MAIN_HAND));
    }

    // choose a viable crafting-table spot near the player according to TablePriority
    private BlockPos pickTableSpot() {
        final Vec3d eye = EntityHelper.getEyePos(Mint.mc.player);
        final double reach = Math.max(range.getValue(), wallsRange.getValue());
        final int r = (int) Math.ceil(tableRange.getValue());
        final BlockPos base = Mint.mc.player.getBlockPos();

        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;

        for (int ox = -r; ox <= r; ox++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int oz = -r; oz <= r; oz++) {
                    BlockPos pos = base.add(ox, oy, oz);
                    double dist = Vec3d.ofCenter(pos).distanceTo(eye);
                    if (dist > tableRange.getValue() + 0.5) continue;
                    if (!withinReach(pos, reach)) continue;
                    if (!WorldHelper.isReplaceable(pos)) continue;
                    if (!WorldHelper.canPlaceAt(pos)) continue;
                    if (!WorldHelper.isValidPlacement(pos, Blocks.CRAFTING_TABLE)) continue;
                    if (!PlaceHelper.isEmpty(pos)) continue;
                    if (WorldHelper.isReplaceable(pos.down())) continue; // need a floor

                    double score;
                    switch (tablePriority.getValue()) {
                        case Down -> {
                            double drop = Mint.mc.player.getY() - pos.getY();
                            if (drop <= 0 || drop > tableDownRange.getValue()) continue;
                            score = drop - dist * 0.01; // prefer lowest, tie-break by closeness
                        }
                        case Hole -> {
                            boolean hole = WorldHelper.isHole(pos) || WorldHelper.isSafeHole(pos);
                            if (!hole) continue;
                            score = -dist; // among holes, closest
                        }
                        default -> score = -dist; // Closest
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void craftBeds(CraftingScreenHandler handler) {
        int free = freeSlots(handler);
        int cap = (int) Math.min(bedsPerCraft.getValue(), free - minFreeSlots.getValue());
        if (cap <= 0) return;

        for (int n = 0; n < cap; n++) {
            int woolSlot = findIngredientSlot(handler, true);
            int plankSlot = findIngredientSlot(handler, false);
            if (woolSlot < 0 || plankSlot < 0) break;

            // wool -> top row (grid slots 1,2,3)
            click(handler, woolSlot, 0, SlotActionType.PICKUP);
            click(handler, 1, 1, SlotActionType.PICKUP);
            click(handler, 2, 1, SlotActionType.PICKUP);
            click(handler, 3, 1, SlotActionType.PICKUP);
            click(handler, woolSlot, 0, SlotActionType.PICKUP); // return remainder

            // planks -> middle row (grid slots 4,5,6)
            click(handler, plankSlot, 0, SlotActionType.PICKUP);
            click(handler, 4, 1, SlotActionType.PICKUP);
            click(handler, 5, 1, SlotActionType.PICKUP);
            click(handler, 6, 1, SlotActionType.PICKUP);
            click(handler, plankSlot, 0, SlotActionType.PICKUP);

            // craft + move result to inventory
            click(handler, 0, 0, SlotActionType.QUICK_MOVE);
        }
    }

    private void click(ScreenHandler handler, int slot, int button, SlotActionType action) {
        Mint.mc.interactionManager.clickSlot(handler.syncId, slot, button, action, Mint.mc.player);
    }

    // count empty player-inventory slots within the open handler (indices after the 3x3 grid)
    private int freeSlots(CraftingScreenHandler handler) {
        int free = 0;
        for (int i = 10; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).getStack().isEmpty()) free++;
        }
        return free;
    }

    // find a player-inventory slot in the handler holding >=3 wool (same colour) or >=3 planks
    private int findIngredientSlot(CraftingScreenHandler handler, boolean wool) {
        int best = -1;
        int bestCount = 2; // need at least 3
        for (int i = 10; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            boolean match = wool ? stack.isIn(ItemTags.WOOL) : stack.isIn(ItemTags.PLANKS);
            if (!match) continue;
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                best = i;
            }
        }
        return best;
    }

    private record ExistingBed(BlockPos pos, LivingEntity target, double damage) {}

    private record PlaceData(BlockPos foot, Direction dir, BlockPos detonate, LivingEntity target, double damage, double score) {}
}
