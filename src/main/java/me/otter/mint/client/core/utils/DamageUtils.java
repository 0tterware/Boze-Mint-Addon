package me.otter.mint.client.core.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

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
        BlockState blockState = mc.world.getBlockState(blockPos);
        if (blockState.getBlock().getBlastResistance() < 600) return null;

        return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
    };


    public static float anchorDamage(LivingEntity target, Vec3d anchor) {
        return anchorDamage(target, anchor, false);
    }

    public static float anchorDamage(LivingEntity target, Vec3d anchor, boolean predictMovement) {
        return anchorDamage(target, anchor, predictMovement, false);
    }

    public static float anchorDamage(LivingEntity target, Vec3d anchor, boolean predictMovement, boolean assumeMaxArmor) {
        return overridingExplosionDamage(target, anchor, 10f, predictMovement, BlockPos.ofFloored(anchor), Blocks.AIR.getDefaultState(), assumeMaxArmor);
    }

    public static float crystalDamage(LivingEntity target, Vec3d crystal) {
        return explosionDamage(target, crystal, 12f, false, HIT_FACTORY);
    }

    public static float bedDamage(LivingEntity target, Vec3d bed) {
        return explosionDamage(target, bed, 10f, false, HIT_FACTORY);
    }

    private static float overridingExplosionDamage(LivingEntity target, Vec3d explosionPos, float power, boolean predictMovement, BlockPos overridePos, BlockState overrideState) {
        return overridingExplosionDamage(target, explosionPos, power, predictMovement, overridePos, overrideState, false);
    }

    private static float overridingExplosionDamage(LivingEntity target, Vec3d explosionPos, float power, boolean predictMovement, BlockPos overridePos, BlockState overrideState, boolean assumeMaxArmor) {
        return explosionDamage(target, explosionPos, power, predictMovement, getOverridingHitFactory(overridePos, overrideState), assumeMaxArmor);
    }

    public static float explosionDamage(LivingEntity target, Vec3d explosionPos, float power, boolean predictMovement, RaycastFactory raycastFactory) {
        return explosionDamage(target, explosionPos, power, predictMovement, raycastFactory, false);
    }

    public static float explosionDamage(LivingEntity target, Vec3d explosionPos, float power, boolean predictMovement, RaycastFactory raycastFactory, boolean assumeMaxArmor) {
        if (target == null || mc.world == null) return 0f;
        if (target instanceof PlayerEntity player && player.isCreative()) return 0f;

        Vec3d position = new Vec3d(target.getX(), target.getY(), target.getZ());
        Box box = target.getBoundingBox();
        if (predictMovement) {
            Vec3d velocity = target.getVelocity();
            position = position.add(velocity);
            box = box.offset(velocity);
        }

        return explosionDamage(target, position, box, explosionPos, power, raycastFactory, assumeMaxArmor);
    }

    public static float explosionDamage(LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d explosionPos, float power, RaycastFactory raycastFactory) {
        return explosionDamage(target, targetPos, targetBox, explosionPos, power, raycastFactory, false);
    }

    public static float explosionDamage(LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d explosionPos, float power, RaycastFactory raycastFactory, boolean assumeMaxArmor) {
        double distance = targetPos.distanceTo(explosionPos);
        if (distance > power) return 0f;

        double exposure = getExposure(explosionPos, targetBox, raycastFactory);
        double impact = (1 - (distance / power)) * exposure;
        float damage = (int) ((impact * impact + impact) / 2 * 7 * power + 1);

        return calculateReductions(damage, target, mc.world.getDamageSources().explosion(null), assumeMaxArmor);
    }

    public static RaycastFactory getOverridingHitFactory(BlockPos overridePos, BlockState overrideState) {
        return (context, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(overridePos)) blockState = overrideState;
            else {
                blockState = mc.world.getBlockState(blockPos);
                if (blockState.getBlock().getBlastResistance() < 600) return null;
            }

            return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
        };
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        return calculateReductions(damage, entity, damageSource, false);
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource, boolean assumeMaxArmor) {
        // Armor reduction (assume full netherite-equivalent armor + toughness when hiding armor info)
        float armor = assumeMaxArmor ? 20f : entity.getArmor();
        float toughness = assumeMaxArmor ? 12f : (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        damage = DamageUtil.getDamageLeft(entity, damage, damageSource, armor, toughness);

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection (enchantment) reduction
        damage = protectionReduction(entity, damage, damageSource, assumeMaxArmor);

        return Math.max(damage, 0f);
    }

    private static float resistanceReduction(LivingEntity entity, float damage) {
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
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
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        if (assumeMaxArmor) return DamageUtil.getInflictedDamage(damage, 20);

        int protection = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
            for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(enchantment);
                if (level <= 0) continue;

                if (enchantment.matchesKey(Enchantments.PROTECTION)) {
                    protection += level;
                } else if (enchantment.matchesKey(Enchantments.BLAST_PROTECTION) && source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                    protection += 2 * level;
                } else if (enchantment.matchesKey(Enchantments.FIRE_PROTECTION) && source.isIn(DamageTypeTags.IS_FIRE)) {
                    protection += 2 * level;
                } else if (enchantment.matchesKey(Enchantments.PROJECTILE_PROTECTION) && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                    protection += 2 * level;
                } else if (enchantment.matchesKey(Enchantments.FEATHER_FALLING) && source.isIn(DamageTypeTags.IS_FALL)) {
                    protection += 3 * level;
                }
            }
        }

        return DamageUtil.getInflictedDamage(damage, protection);
    }

    // Exposure
    private static float getExposure(Vec3d source, Box box, RaycastFactory raycastFactory) {
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
                    Vec3d position = new Vec3d(x, y, z);

                    if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;

                    hits++;
                }
            }
        }

        return (float) misses / hits;
    }

    private static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
        return BlockView.raycast(context.start(), context.end(), context, raycastFactory, ctx -> null);
    }

    public record ExposureRaycastContext(Vec3d start, Vec3d end) {
    }

    @FunctionalInterface
    public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> {
    }
}
