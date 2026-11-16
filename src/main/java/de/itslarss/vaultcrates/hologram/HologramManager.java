package de.itslarss.vaultcrates.hologram;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;

/**
 * Manages all crate holograms.
 *
 * <p>Holograms are non-persistent {@link org.bukkit.entity.TextDisplay} entities, so the
 * server discards them whenever their chunk unloads. This manager therefore keeps every
 * registered hologram even while it is not in the world, spawns it only into loaded
 * chunks, and re-spawns it each time its chunk loads again.</p>
 */
public class HologramManager implements Listener {

    private final VaultCrates plugin;

    /** All registered holograms keyed by ID — spawned or waiting for their chunk to load. */
    private final Map<String, CrateHologram> holograms = new LinkedHashMap<>();

    public HologramManager(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Hologram management
    // -------------------------------------------------------------------------

    /**
     * Registers a hologram and spawns it if its chunk is loaded; otherwise it
     * appears as soon as the chunk loads. An existing hologram with the same ID
     * is replaced.
     *
     * @param id    the unique identifier (typically the serialised location)
     * @param base  the block location above which the hologram appears
     * @param lines the text lines (top to bottom)
     */
    public void spawnHologram(String id, Location base, List<String> lines) {
        if (base == null || !base.isWorldLoaded() || lines == null || lines.isEmpty()) return;
        removeHologram(id);
        CrateHologram hologram = new CrateHologram(id, base, lines);
        holograms.put(id, hologram);
        if (isChunkLoaded(base)) hologram.spawn();
    }

    /** Unregisters the hologram with the given ID and removes it from the world. */
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
        if (existing == null) return;
        if (isChunkLoaded(existing.getBaseLocation())) existing.update(newLines);
        else existing.setLines(newLines);
    }

    /** Returns {@code true} if a hologram with the given ID is registered. */
    public boolean hasHologram(String id) {
        return holograms.containsKey(id);
    }

    /** Returns the hologram with the given ID, or {@code null}. */
    public CrateHologram getHologram(String id) {
        return holograms.get(id);
    }

    /** Returns an unmodifiable view of all registered holograms. */
    public Collection<CrateHologram> getHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    /** Unregisters and removes all holograms. */
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
    // Chunk tracking
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (inChunk(chunk).isEmpty()) return;
        // Spawn on the next tick — adding entities while the chunk is still loading is unsafe
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;
            for (CrateHologram hologram : inChunk(chunk)) {
                if (!hologram.isSpawned()) hologram.spawn();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // The server would discard the non-persistent displays anyway; drop our
        // references so the next chunk load spawns fresh ones
        for (CrateHologram hologram : inChunk(event.getChunk())) hologram.remove();
    }

    private List<CrateHologram> inChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX(), cz = chunk.getZ();
        List<CrateHologram> result = new ArrayList<>(0);
        for (CrateHologram hologram : holograms.values()) {
            Location base = hologram.getBaseLocation();
            if (base.isWorldLoaded() && world.equals(base.getWorld())
                    && base.getBlockX() >> 4 == cx && base.getBlockZ() >> 4 == cz) {
                result.add(hologram);
            }
        }
        return result;
    }

    private static boolean isChunkLoaded(Location loc) {
        return loc.isWorldLoaded()
                && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
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
