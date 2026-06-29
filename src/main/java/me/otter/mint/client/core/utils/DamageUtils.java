package me.otter.mint.client.core.utils;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;

import java.util.function.BiFunction;

import static me.otter.mint.Mint.mc;

// 1 billion % skidded from meteor
public class DamageUtils {
    private DamageUtils() {
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static final RaycastFactory HIT_FACTORY = (context, blockPos) -> {
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (blockState.getBlock().getExplosionResistance() < 600) return null;

        return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
    };


    public static float anchorDamage(LivingEntity target, Vec3 anchor) {
        return anchorDamage(target, anchor, false);
    }

    public static float anchorDamage(LivingEntity target, Vec3 anchor, boolean predictMovement) {
        return anchorDamage(target, anchor, predictMovement, false);
    }

    public static float anchorDamage(LivingEntity target, Vec3 anchor, boolean predictMovement, boolean assumeMaxArmor) {
        return overridingExplosionDamage(target, anchor, 10f, predictMovement, BlockPos.containing(anchor), Blocks.AIR.defaultBlockState(), assumeMaxArmor);
    }

    public static float crystalDamage(LivingEntity target, Vec3 crystal) {
        return explosionDamage(target, crystal, 12f, false, HIT_FACTORY);
    }

    public static float bedDamage(LivingEntity target, Vec3 bed) {
        return explosionDamage(target, bed, 10f, false, HIT_FACTORY);
    }

    private static float overridingExplosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, BlockPos overridePos, BlockState overrideState) {
        return overridingExplosionDamage(target, explosionPos, power, predictMovement, overridePos, overrideState, false);
    }

    private static float overridingExplosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, BlockPos overridePos, BlockState overrideState, boolean assumeMaxArmor) {
        return explosionDamage(target, explosionPos, power, predictMovement, getOverridingHitFactory(overridePos, overrideState), assumeMaxArmor);
    }

    public static float explosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, RaycastFactory raycastFactory) {
        return explosionDamage(target, explosionPos, power, predictMovement, raycastFactory, false);
    }

    public static float explosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, RaycastFactory raycastFactory, boolean assumeMaxArmor) {
        if (target == null || mc.level == null) return 0f;
        if (target instanceof Player player && player.isCreative()) return 0f;

        Vec3 position = new Vec3(target.getX(), target.getY(), target.getZ());
        AABB box = target.getBoundingBox();
        if (predictMovement) {
            Vec3 velocity = target.getDeltaMovement();
            position = position.add(velocity);
            box = box.move(velocity);
        }

        return explosionDamage(target, position, box, explosionPos, power, raycastFactory, assumeMaxArmor);
    }

    public static float explosionDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
        return explosionDamage(target, targetPos, targetBox, explosionPos, power, raycastFactory, false);
    }

    public static float explosionDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, float power, RaycastFactory raycastFactory, boolean assumeMaxArmor) {
        double distance = targetPos.distanceTo(explosionPos);
        if (distance > power) return 0f;

        double exposure = getExposure(explosionPos, targetBox, raycastFactory);
        double impact = (1 - (distance / power)) * exposure;
        float damage = (int) ((impact * impact + impact) / 2 * 7 * power + 1);

        return calculateReductions(damage, target, mc.level.damageSources().explosion(null), assumeMaxArmor);
    }

    public static RaycastFactory getOverridingHitFactory(BlockPos overridePos, BlockState overrideState) {
        return (context, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(overridePos)) blockState = overrideState;
            else {
                blockState = mc.level.getBlockState(blockPos);
                if (blockState.getBlock().getExplosionResistance() < 600) return null;
            }

            return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
        };
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        return calculateReductions(damage, entity, damageSource, false);
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource, boolean assumeMaxArmor) {
        // Armor reduction (assume full netherite-equivalent armor + toughness when hiding armor info)
        float armor = assumeMaxArmor ? 20f : entity.getArmorValue();
        float toughness = assumeMaxArmor ? 12f : (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, armor, toughness);

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection (enchantment) reduction
        damage = protectionReduction(entity, damage, damageSource, assumeMaxArmor);

        return Math.max(damage, 0f);
    }

    private static float resistanceReduction(LivingEntity entity, float damage) {
        MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= (1 - (lvl * 0.2f));
        }

        return Math.max(damage, 0f);
    }

    private static float protectionReduction(LivingEntity entity, float damage, DamageSource source) {
        return protectionReduction(entity, damage, source, false);
    }

    private static float protectionReduction(LivingEntity entity, float damage, DamageSource source, boolean assumeMaxArmor) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        if (assumeMaxArmor) return CombatRules.getDamageAfterMagicAbsorb(damage, 20);

        int protection = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
            for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                int level = enchantments.getLevel(enchantment);
                if (level <= 0) continue;

                if (enchantment.is(Enchantments.PROTECTION)) {
                    protection += level;
                } else if (enchantment.is(Enchantments.BLAST_PROTECTION) && source.is(DamageTypeTags.IS_EXPLOSION)) {
                    protection += 2 * level;
                } else if (enchantment.is(Enchantments.FIRE_PROTECTION) && source.is(DamageTypeTags.IS_FIRE)) {
                    protection += 2 * level;
                } else if (enchantment.is(Enchantments.PROJECTILE_PROTECTION) && source.is(DamageTypeTags.IS_PROJECTILE)) {
                    protection += 2 * level;
                } else if (enchantment.is(Enchantments.FEATHER_FALLING) && source.is(DamageTypeTags.IS_FALL)) {
                    protection += 3 * level;
                }
            }
        }

        return CombatRules.getDamageAfterMagicAbsorb(damage, protection);
    }

    // Exposure
    private static float getExposure(Vec3 source, AABB box, RaycastFactory raycastFactory) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1 / (xDiff * 2 + 1);
        double yStep = 1 / (yDiff * 2 + 1);
        double zStep = 1 / (zDiff * 2 + 1);

        if (xStep <= 0 || yStep <= 0 || zStep <= 0) return 0f;

        int misses = 0;
        int hits = 0;

        double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
        double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;

        xStep = xStep * xDiff;
        yStep = yStep * yDiff;
        zStep = zStep * zDiff;

        double startX = box.minX + xOffset;
        double startY = box.minY;
        double startZ = box.minZ + zOffset;
        double endX = box.maxX + xOffset;
        double endY = box.maxY;
        double endZ = box.maxZ + zOffset;

        for (double x = startX; x <= endX; x += xStep) {
            for (double y = startY; y <= endY; y += yStep) {
                for (double z = startZ; z <= endZ; z += zStep) {
                    Vec3 position = new Vec3(x, y, z);

                    if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;

                    hits++;
                }
            }
        }

        return (float) misses / hits;
    }

    private static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
        return BlockGetter.traverseBlocks(context.start(), context.end(), context, raycastFactory, ctx -> null);
    }

    public record ExposureRaycastContext(Vec3 start, Vec3 end) {
    }

    @FunctionalInterface
    public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> {
    }
}
