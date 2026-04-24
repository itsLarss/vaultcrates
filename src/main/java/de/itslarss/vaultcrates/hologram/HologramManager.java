package de.itslarss.vaultcrates.hologram;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Location;

import java.util.*;

/**
 * Manages all active crate holograms in the world.
 * Holograms are spawned as {@link org.bukkit.entity.TextDisplay} entities and are
 * non-persistent, so they must be re-created on every server start.
 */
public class HologramManager {

    private final VaultCrates plugin;

    /** Active holograms keyed by their unique ID. */
    private final Map<String, CrateHologram> holograms = new LinkedHashMap<>();

    public HologramManager(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Hologram management
    // -------------------------------------------------------------------------

    /**
     * Spawns a new hologram at the base location with the given lines.
     * If a hologram with the same ID already exists it is replaced.
     *
     * @param id    the unique identifier (typically the serialised location)
     * @param base  the block location above which the hologram appears
     * @param lines the text lines (top to bottom)
     */
    public void spawnHologram(String id, Location base, List<String> lines) {
        if (base == null || base.getWorld() == null || lines == null || lines.isEmpty()) return;
        removeHologram(id);
        CrateHologram hologram = new CrateHologram(id, base, lines);
        hologram.spawn();
        if (hologram.isSpawned()) {
            holograms.put(id, hologram);
        }
    }

    /** Removes the hologram with the given ID. */
    public void removeHologram(String id) {
        CrateHologram existing = holograms.remove(id);
        if (existing != null) existing.remove();
    }

    /** Removes the hologram at the given location. */
    public void removeHologram(Location loc) {
        removeHologram(locationToId(loc));
    }

    /**
     * Updates the text of an existing hologram.
     * If no hologram with the given ID exists, a new one is not created.
     */
    public void updateHologram(String id, List<String> newLines) {
        CrateHologram existing = holograms.get(id);
        if (existing != null) existing.update(newLines);
    }

    /** Returns {@code true} if a hologram with the given ID is registered. */
    public boolean hasHologram(String id) {
        return holograms.containsKey(id);
    }

    /** Returns the hologram with the given ID, or {@code null}. */
    public CrateHologram getHologram(String id) {
        return holograms.get(id);
    }

    /** Returns an unmodifiable view of all active holograms. */
    public Collection<CrateHologram> getHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    /** Removes all active holograms. */
    public void removeAll() {
        holograms.values().forEach(CrateHologram::remove);
        holograms.clear();
    }

    /**
     * Re-creates all crate holograms from the current crate location data.
     * Called on enable and after a reload.
     */
    public void reloadAll() {
        removeAll();
        for (Map.Entry<Location, Crate> entry : plugin.getCrateManager().getCrateLocations().entrySet()) {
            Location loc = entry.getKey();
            Crate crate = entry.getValue();
            if (!crate.getHologramLines().isEmpty()) {
                spawnHologram(locationToId(loc), loc, crate.getHologramLines());
            }
        }
    }

    /**
     * Spawns a temporary hologram that is automatically removed after a delay.
     *
     * @param base          the base location
     * @param lines         the hologram lines
     * @param durationTicks how many ticks before the hologram is removed
     */
    public void spawnTemporaryHologram(Location base, List<String> lines, long durationTicks) {
        String tempId = "temp_" + System.nanoTime();
        spawnHologram(tempId, base, lines);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeHologram(tempId), durationTicks);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a location to a string ID for use as a hologram map key.
     */
    public String locationToId(Location loc) {
        return LocationUtil.serialize(loc.getBlock().getLocation());
    }
}
