package de.itslarss.vaultcrates.crate;

import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the physical placement of a crate in the world.
 *
 * <p>Each placement has a unique short ID (e.g. {@code a1b2c3}) so that
 * multiple crates of the same type can coexist and be removed individually
 * via {@code /vc remove <id>}.</p>
 *
 * <p>Two placement types are supported:</p>
 * <ul>
 *   <li><b>Block crate</b> — a vanilla block (e.g. ENDER_CHEST) placed in the world.
 *       {@link #getEntityUuid()} returns {@code null}.</li>
 *   <li><b>Furniture crate</b> — an ItemsAdder furniture entity (ArmorStand).
 *       {@link #getEntityUuid()} returns the entity's UUID.
 *       The {@link #getLocation()} is still stored for hologram placement.</li>
 * </ul>
 */
public class CrateLocation {

    private final String   placementId;
    private final Location location;
    private final String   crateName;

    /**
     * Non-null for furniture (entity-based) crates; {@code null} for vanilla block crates.
     * Mutable so it can be set once the ArmorStand entity is resolved after placement.
     */
    @Nullable
    private UUID entityUuid;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Block-crate constructor — no entity UUID. */
    public CrateLocation(String placementId, Location location, String crateName) {
        this(placementId, location, crateName, null);
    }

    /** Universal constructor. */
    public CrateLocation(String placementId, Location location, String crateName, @Nullable UUID entityUuid) {
        this.placementId = placementId;
        this.location    = location;
        this.crateName   = crateName;
        this.entityUuid  = entityUuid;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the unique placement ID for this instance (e.g. {@code "a1b2c3"}). */
    public String getPlacementId() { return placementId; }

    /**
     * Returns the world location.
     * <ul>
     *   <li>Block crate — the block's location.</li>
     *   <li>Furniture crate — the entity's block-floor location (used for hologram).</li>
     * </ul>
     */
    public Location getLocation() { return location; }

    /** Returns the name of the crate type at this location. */
    public String getCrateName() { return crateName; }

    /**
     * Returns the entity UUID for furniture crates, or {@code null} for block crates.
     */
    @Nullable
    public UUID getEntityUuid() { return entityUuid; }

    /**
     * Updates the entity UUID.
     * Called once the ArmorStand furniture is spawned and resolved.
     */
    public void setEntityUuid(@Nullable UUID uuid) { this.entityUuid = uuid; }

    /** Returns {@code true} if this is a furniture (entity-based) crate placement. */
    public boolean isFurnitureCrate() { return entityUuid != null; }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    /**
     * Serialises the location to {@code "world,x,y,z"} format.
     */
    public String serialize() {
        return LocationUtil.serialize(location);
    }

    /**
     * Deserialises a block-crate placement from its stored parts.
     *
     * @param placementId the unique placement ID
     * @param locStr      the serialised location string ({@code "world,x,y,z"})
     * @param crateName   the name of the crate
     * @return the parsed CrateLocation, or {@code null} if the world is not loaded
     */
    public static CrateLocation deserialize(String placementId, String locStr, String crateName) {
        return deserialize(placementId, locStr, crateName, null);
    }

    /**
     * Deserialises a placement (block or furniture) from its stored parts.
     *
     * @param placementId the unique placement ID
     * @param locStr      the serialised location string ({@code "world,x,y,z"})
     * @param crateName   the name of the crate
     * @param entityUuid  the furniture entity UUID, or {@code null} for block crates
     * @return the parsed CrateLocation, or {@code null} if the world is not loaded
     */
    public static CrateLocation deserialize(String placementId, String locStr,
                                            String crateName, @Nullable UUID entityUuid) {
        Location loc = LocationUtil.deserialize(locStr);
        if (loc == null) return null;
        return new CrateLocation(placementId, loc.getBlock().getLocation(), crateName, entityUuid);
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

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
        return "CrateLocation{id=" + placementId + ", crate=" + crateName
                + ", loc=" + serialize()
                + (entityUuid != null ? ", entity=" + entityUuid : "")
                + "}";
    }
}
