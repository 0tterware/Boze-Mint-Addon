package me.otter.mint.client.core.utils;

import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.PlaceHelper;
import me.otter.mint.Mint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public record WitherLayout(WitherDirection witherDirection, WitherArmAxis witherArmAxis) {

    public enum WitherDirection {Up, Down, SideNorth, SideSouth, SideEast, SideWest}
    public enum WitherArmAxis {EastWest, NorthSouth, UpDown}
    public List<BlockPos> getSoulOffsets(BlockPos base) {
        List<BlockPos> soulBlocks = new ArrayList<>(4);
        BlockPos middle = getMiddle(base);

        soulBlocks.add(base);
        soulBlocks.add(middle);

        switch (witherDirection) {
            case Up, Down -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    soulBlocks.add(middle.east());
                    soulBlocks.add(middle.west());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    soulBlocks.add(middle.north());
                    soulBlocks.add(middle.south());
                }
            }
            case SideEast, SideWest -> {
                if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    soulBlocks.add(middle.north());
                    soulBlocks.add(middle.south());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    soulBlocks.add(middle.above());
                    soulBlocks.add(middle.below());
                }
            }
            case SideNorth, SideSouth -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    soulBlocks.add(middle.east());
                    soulBlocks.add(middle.west());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    soulBlocks.add(middle.above());
                    soulBlocks.add(middle.below());
                }
            }
        }
        return soulBlocks;
    }

    public List<BlockPos> getSkullOffsets(BlockPos base) {
        List<BlockPos> skullBlocks = new ArrayList<>(3);
        BlockPos middle = getMiddle(base);
        BlockPos middleSkull = getMiddle(middle);

        skullBlocks.add(middleSkull);

        switch (witherDirection) {
            case Up, Down -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    skullBlocks.add(middleSkull.east());
                    skullBlocks.add(middleSkull.west());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    skullBlocks.add(middleSkull.north());
                    skullBlocks.add(middleSkull.south());
                }
            }
            case SideNorth, SideSouth -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    skullBlocks.add(middleSkull.east());
                    skullBlocks.add(middleSkull.west());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    skullBlocks.add(middleSkull.above());
                    skullBlocks.add(middleSkull.below());
                }
            }
            case SideEast, SideWest -> {
                if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    skullBlocks.add(middleSkull.north());
                    skullBlocks.add(middleSkull.south());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    skullBlocks.add(middleSkull.above());
                    skullBlocks.add(middleSkull.below());
                }
            }
        }
        return skullBlocks;
    }

    public List<BlockPos> getAirOffsets(BlockPos base) {
        List<BlockPos> airBlocks = new ArrayList<>(2);
        BlockPos middle = getMiddle(base);
        Direction back = opposite(witherDirection);

        switch (witherDirection) {
            case Up -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    airBlocks.add(middle.east().below());
                    airBlocks.add(middle.west().below());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    airBlocks.add(middle.north().below());
                    airBlocks.add(middle.south().below());
                }
            }
            case Down -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    airBlocks.add(middle.east().above());
                    airBlocks.add(middle.west().above());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    airBlocks.add(middle.north().above());
                    airBlocks.add(middle.south().above());
                }
            }
            case SideNorth, SideSouth -> {
                if (witherArmAxis == WitherArmAxis.UpDown) {
                    airBlocks.add(middle.above().relative(back));
                    airBlocks.add(middle.below().relative(back));
                } else {
                    airBlocks.add(middle.east().relative(back));
                    airBlocks.add(middle.west().relative(back));
                }
            }
            case SideWest, SideEast -> {
                if (witherArmAxis == WitherArmAxis.UpDown) {
                    airBlocks.add(middle.above().relative(back));
                    airBlocks.add(middle.below().relative(back));
                } else {
                    airBlocks.add(middle.north().relative(back));
                    airBlocks.add(middle.south().relative(back));
                }
            }
        }
        return airBlocks;
    }

    private BlockPos getMiddle(BlockPos base) {
        return switch (witherDirection) {
            case Up -> base.above();
            case Down -> base.below();
            case SideNorth -> base.relative(Direction.NORTH);
            case SideSouth -> base.relative(Direction.SOUTH);
            case SideEast -> base.relative(Direction.EAST);
            case SideWest -> base.relative(Direction.WEST);
        };
    }

    private static Direction opposite(WitherDirection direction) {
        return switch (direction) {
            case Up -> Direction.DOWN;
            case Down -> Direction.UP;
            case SideNorth -> Direction.SOUTH;
            case SideSouth -> Direction.NORTH;
            case SideEast -> Direction.WEST;
            case SideWest -> Direction.EAST;
        };
    }

    public static List<WitherLayout> findLayoutsAt(BlockPos base) {
        List<WitherLayout> validLayouts = new ArrayList<>(WITHER_LAYOUTS.length);

        for (WitherLayout layout : WITHER_LAYOUTS) {
            List<BlockPos> souls = layout.getSoulOffsets(base);

            if (!allReplaceableAndEmpty(souls)) continue;

            if (!allReplaceableAndEmpty(layout.getSkullOffsets(base))) continue;

            if (!allAir(layout.getAirOffsets(base))) continue;

            if (!hasSupport(souls)) continue;

            validLayouts.add(layout);
        }
        return validLayouts;
    }

    private static boolean hasSupport(List<BlockPos> positions) {
        for (BlockPos p : positions) {
            for (Direction dir : Direction.values()) {
                if (!WorldHelper.isReplaceable(p.relative(dir))) return true;
            }
        }
        return false;
    }

    private static boolean allReplaceableAndEmpty(List<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (!WorldHelper.isReplaceable(p)) return false;
            if (!PlaceHelper.isEmpty(p)) return false;
        }
        return true;
    }

    private static boolean allAir(List<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (!Mint.mc.level.getBlockState(p).isAir()) return false;
        }
        return true;
    }

    public static final WitherLayout[] WITHER_LAYOUTS = new WitherLayout[] {
            new WitherLayout(WitherDirection.Up, WitherArmAxis.EastWest),
            new WitherLayout(WitherDirection.Up, WitherArmAxis.NorthSouth),
            new WitherLayout(WitherDirection.Down, WitherArmAxis.EastWest),
            new WitherLayout(WitherDirection.Down, WitherArmAxis.NorthSouth),

            new WitherLayout(WitherDirection.SideNorth, WitherArmAxis.EastWest),
            new WitherLayout(WitherDirection.SideSouth, WitherArmAxis.EastWest),
            new WitherLayout(WitherDirection.SideEast, WitherArmAxis.NorthSouth),
            new WitherLayout(WitherDirection.SideWest, WitherArmAxis.NorthSouth),

            new WitherLayout(WitherDirection.SideNorth, WitherArmAxis.UpDown),
            new WitherLayout(WitherDirection.SideSouth, WitherArmAxis.UpDown),
            new WitherLayout(WitherDirection.SideEast, WitherArmAxis.UpDown),
            new WitherLayout(WitherDirection.SideWest, WitherArmAxis.UpDown),
    };
}