package de.itslarss.vaultcrates.crate;

import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Location;

import java.util.Objects;

/**
 * Represents the physical placement of a crate in the world.
 * Each placement has a unique short ID (e.g. {@code a1b2c3}) so that
 * multiple crates of the same type can coexist and be removed individually
 * via {@code /vc remove <id>}.
 */
public class CrateLocation {

    private final String placementId;
    private final Location location;
    private final String crateName;

    public CrateLocation(String placementId, Location location, String crateName) {
        this.placementId = placementId;
        this.location    = location;
        this.crateName   = crateName;
    }

    /** Returns the unique placement ID for this instance (e.g. {@code "a1b2c3"}). */
    public String getPlacementId() { return placementId; }

    /** Returns the block location where the crate is placed. */
    public Location getLocation() { return location; }

    /** Returns the name of the crate type at this location. */
    public String getCrateName() { return crateName; }

    /**
     * Serialises the block location to {@code "world,x,y,z"} format.
     */
    public String serialize() {
        return LocationUtil.serialize(location);
    }

    /**
     * Deserialises a crate placement from its stored parts.
     *
     * @param placementId the unique placement ID
     * @param locStr      the serialised location string ({@code "world,x,y,z"})
     * @param crateName   the name of the crate
     * @return the parsed CrateLocation, or {@code null} if the world is not loaded
     */
    public static CrateLocation deserialize(String placementId, String locStr, String crateName) {
        Location loc = LocationUtil.deserialize(locStr);
        if (loc == null) return null;
        return new CrateLocation(placementId, loc.getBlock().getLocation(), crateName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrateLocation other)) return false;
        return Objects.equals(placementId, other.placementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placementId);
    }

    @Override
    public String toString() {
        return "CrateLocation{id=" + placementId + ", crate=" + crateName + ", loc=" + serialize() + "}";
    }
}
