package de.itslarss.vaultcrates.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility methods for serialising and deserialising {@link Location} objects,
 * and for common proximity checks.
 */
public final class LocationUtil {

    private LocationUtil() {
        // Utility class — no instantiation
    }

    // -------------------------------------------------------------------------
    // Serialisation / deserialisation
    // -------------------------------------------------------------------------

    /**
     * Serialises a location to the string {@code "world,x,y,z"} using integer
     * block coordinates.
     *
     * @param loc the location to serialise (must not be {@code null})
     * @return a compact string representation
     */
    public static String serialize(Location loc) {
        return loc.getWorld().getName()
                + "," + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
    }

    /**
     * Alias for {@link #serialize(Location)} — returns {@code "world,x,y,z"}
     * with integer block coordinates.
     */
    public static String toBlockString(Location loc) {
        return serialize(loc);
    }

    /**
     * Deserialises a string produced by {@link #serialize(Location)} back into a
     * {@link Location}, placing the entity at the centre of the block (x+0.5,
     * y exactly, z+0.5).
     *
     * @param s the serialised string
     * @return the corresponding {@link Location}, or {@code null} if the world
     *         cannot be found or the string is malformed
     */
    public static Location deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        if (parts.length < 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Proximity / centering
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if locations {@code a} and {@code b} are in the same
     * world and within {@code radius} blocks of each other.
     *
     * @param a      the first location
     * @param b      the second location
     * @param radius the maximum allowed distance
     * @return {@code true} if within range
     */
    public static boolean isNearby(Location a, Location b, double radius) {
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.distance(b) <= radius;
    }

    /**
     * Returns a new location placed at the centre of the block column that
     * {@code loc} falls in (x+0.5, same y, z+0.5).
     *
     * @param loc the reference location
     * @return a new centred {@link Location}
     */
    public static Location centerOf(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX() + 0.5,
                loc.getBlockY(),
                loc.getBlockZ() + 0.5
        );
    }
}
