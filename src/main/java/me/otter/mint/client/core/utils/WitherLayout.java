package me.otter.mint.client.core.utils;

import dev.boze.api.utility.WorldHelper;
import dev.boze.api.utility.interaction.PlaceHelper;
import me.otter.mint.Mint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
                    soulBlocks.add(middle.up());
                    soulBlocks.add(middle.down());
                }
            }
            case SideNorth, SideSouth -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    soulBlocks.add(middle.east());
                    soulBlocks.add(middle.west());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    soulBlocks.add(middle.up());
                    soulBlocks.add(middle.down());
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
                    skullBlocks.add(middleSkull.up());
                    skullBlocks.add(middleSkull.down());
                }
            }
            case SideEast, SideWest -> {
                if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    skullBlocks.add(middleSkull.north());
                    skullBlocks.add(middleSkull.south());
                } else if (witherArmAxis == WitherArmAxis.UpDown) {
                    skullBlocks.add(middleSkull.up());
                    skullBlocks.add(middleSkull.down());
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
                    airBlocks.add(middle.east().down());
                    airBlocks.add(middle.west().down());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    airBlocks.add(middle.north().down());
                    airBlocks.add(middle.south().down());
                }
            }
            case Down -> {
                if (witherArmAxis == WitherArmAxis.EastWest) {
                    airBlocks.add(middle.east().up());
                    airBlocks.add(middle.west().up());
                } else if (witherArmAxis == WitherArmAxis.NorthSouth) {
                    airBlocks.add(middle.north().up());
                    airBlocks.add(middle.south().up());
                }
            }
            case SideNorth, SideSouth -> {
                if (witherArmAxis == WitherArmAxis.UpDown) {
                    airBlocks.add(middle.up().offset(back));
                    airBlocks.add(middle.down().offset(back));
                } else {
                    airBlocks.add(middle.east().offset(back));
                    airBlocks.add(middle.west().offset(back));
                }
            }
            case SideWest, SideEast -> {
                if (witherArmAxis == WitherArmAxis.UpDown) {
                    airBlocks.add(middle.up().offset(back));
                    airBlocks.add(middle.down().offset(back));
                } else {
                    airBlocks.add(middle.north().offset(back));
                    airBlocks.add(middle.south().offset(back));
                }
            }
        }
        return airBlocks;
    }

    private BlockPos getMiddle(BlockPos base) {
        return switch (witherDirection) {
            case Up -> base.up();
            case Down -> base.down();
            case SideNorth -> base.offset(Direction.NORTH);
            case SideSouth -> base.offset(Direction.SOUTH);
            case SideEast -> base.offset(Direction.EAST);
            case SideWest -> base.offset(Direction.WEST);
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
            if (!allReplaceableAndEmpty(layout.getSoulOffsets(base))) continue;

            if (!allReplaceableAndEmpty(layout.getSkullOffsets(base))) continue;

            if (!allAir(layout.getAirOffsets(base))) continue;

            validLayouts.add(layout);
        }
        return validLayouts;
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
            if (!Mint.mc.world.getBlockState(p).isAir()) return false;
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