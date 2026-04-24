package de.itslarss.vaultcrates.crate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Location;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all loaded crate configurations and their physical world placements.
 *
 * <p>Crate YAML files live in {@code plugins/VaultCrates/crates/}.
 * Placed crate instances are persisted in {@code data/locations.json}.
 * Each placed instance has a short unique ID (e.g. {@code a1b2c3}) so that
 * multiple copies of the same crate type can coexist and be removed
 * individually via {@code /vc remove <id>}.</p>
 *
 * <h3>Migration</h3>
 * {@link de.itslarss.vaultcrates.config.ConfigManager} handles the one-time
 * conversion from the old {@code locations.yml} to {@code data/locations.json}
 * automatically on first load.
 */
public class CrateManager {

    private final VaultCrates plugin;
    private final File cratesFolder;

    /** Map of crate-name (lower-case) → Crate. */
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    /**
     * Map of placement-ID → CrateLocation.
     * The placement-ID is the primary key; location lookup iterates this map.
     */
    private final Map<String, CrateLocation> crateLocations = new LinkedHashMap<>();

    /** Config/parse errors collected during the last reload. */
    private final List<String> errors = new ArrayList<>();

    public CrateManager(VaultCrates plugin) {
        this.plugin = plugin;
        this.cratesFolder = new File(plugin.getDataFolder(), "crates");
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * (Re)loads all crate configurations from the {@code crates/} folder and
     * all placed crate locations from {@code data/locations.json}.
     */
    public void reload() {
        crates.clear();
        errors.clear();

        // Ensure crates folder exists and contains default examples
        if (!cratesFolder.exists()) {
            cratesFolder.mkdirs();
            plugin.saveResource("crates/ExampleRound.yml", false);
        }

        // Load each .yml file
        File[] files = cratesFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    Crate crate = Crate.fromConfig(file.getName(), file);
                    crates.put(crate.getName().toLowerCase(), crate);
                } catch (Exception e) {
                    String msg = "Failed to load crate from " + file.getName() + ": " + e.getMessage();
                    errors.add(msg);
                    plugin.getLogger().log(Level.WARNING, msg, e);
                }
            }
        }

        // Load crate placements
        crateLocations.clear();
        loadCrateLocations();

        plugin.getLogger().info("Loaded " + crates.size() + " crate(s), "
                + crateLocations.size() + " placement(s).");
    }

    // -------------------------------------------------------------------------
    // Location persistence
    // -------------------------------------------------------------------------

    private void loadCrateLocations() {
        JsonObject root        = plugin.getConfigManager().loadLocationsJson();
        JsonElement placementsEl = root.get("placements");
        if (placementsEl == null || !placementsEl.isJsonObject()) return;

        for (Map.Entry<String, JsonElement> entry : placementsEl.getAsJsonObject().entrySet()) {
            String id = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();

            String crateName = obj.has("crate")    ? obj.get("crate").getAsString()    : null;
            String locStr    = obj.has("location") ? obj.get("location").getAsString() : null;
            if (crateName == null || locStr == null) continue;

            CrateLocation cl = CrateLocation.deserialize(id, locStr, crateName);
            if (cl != null) {
                crateLocations.put(id, cl);
            } else {
                plugin.getLogger().warning("Could not load crate placement '" + id
                        + "' (world not loaded yet?). "
                        + "It will be restored on the next reload.");
            }
        }
    }

    /** Persists all current placements to {@code data/locations.json}. */
    public void saveCrateLocations() {
        JsonObject placements = new JsonObject();
        for (Map.Entry<String, CrateLocation> entry : crateLocations.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("crate",    entry.getValue().getCrateName());
            obj.addProperty("location", entry.getValue().serialize());
            placements.add(entry.getKey(), obj);
        }
        JsonObject root = new JsonObject();
        root.add("placements", placements);
        plugin.getConfigManager().saveLocationsJson(root);
    }

    // -------------------------------------------------------------------------
    // Crate lookup
    // -------------------------------------------------------------------------

    /**
     * Gets a crate by name (case-insensitive).
     *
     * @param name the crate name
     * @return the {@link Crate}, or {@code null} if not found
     */
    public Crate getCrate(String name) {
        if (name == null) return null;
        return crates.get(name.toLowerCase());
    }

    /** Returns an unmodifiable view of all loaded crates. */
    public Map<String, Crate> getCrates() {
        return Collections.unmodifiableMap(crates);
    }

    // -------------------------------------------------------------------------
    // Placement management
    // -------------------------------------------------------------------------

    /**
     * Returns the crate placed at the given location, or {@code null}.
     *
     * @param loc any location; block coordinates are used for comparison
     */
    public Crate getCrateAt(Location loc) {
        String target = LocationUtil.serialize(loc.getBlock().getLocation());
        for (CrateLocation cl : crateLocations.values()) {
            if (cl.serialize().equals(target)) return getCrate(cl.getCrateName());
        }
        return null;
    }

    /**
     * Returns the {@link CrateLocation} at the given block position, or {@code null}.
     */
    public CrateLocation getCrateLocationAt(Location loc) {
        String target = LocationUtil.serialize(loc.getBlock().getLocation());
        for (CrateLocation cl : crateLocations.values()) {
            if (cl.serialize().equals(target)) return cl;
        }
        return null;
    }

    /**
     * Registers a new crate placement at the given location, persists to disk,
     * and returns the generated placement ID.
     *
     * @param loc   the block location
     * @param crate the crate to place
     * @return the unique placement ID (display this to the admin)
     */
    public String setCrateAt(Location loc, Crate crate) {
        Location block = loc.getBlock().getLocation();
        String id = generatePlacementId();
        crateLocations.put(id, new CrateLocation(id, block, crate.getName()));
        saveCrateLocations();
        return id;
    }

    /**
     * Removes the crate placement at the given block location and persists to disk.
     * Called by {@link de.itslarss.vaultcrates.listener.CrateListener} on block break.
     *
     * @param loc the block location
     */
    public void removeCrateAt(Location loc) {
        String target = LocationUtil.serialize(loc.getBlock().getLocation());
        crateLocations.entrySet().removeIf(e -> e.getValue().serialize().equals(target));
        saveCrateLocations();
    }

    /**
     * Removes a crate placement by its unique placement ID.
     * Called by {@code /vc remove <id>}.
     *
     * @param id the placement ID
     * @return {@code true} if a placement was found and removed
     */
    public boolean removePlacement(String id) {
        if (crateLocations.remove(id) == null) return false;
        saveCrateLocations();
        return true;
    }

    /**
     * Returns the {@link CrateLocation} for the given placement ID, or {@code null}.
     */
    public CrateLocation getPlacement(String id) {
        return crateLocations.get(id);
    }

    /**
     * Returns an unmodifiable view of all active placements keyed by placement ID.
     */
    public Map<String, CrateLocation> getAllPlacements() {
        return Collections.unmodifiableMap(crateLocations);
    }

    /**
     * Returns a snapshot map of all placed crate locations (Location → Crate).
     * Used by {@link de.itslarss.vaultcrates.hologram.HologramManager}.
     * Only includes entries whose crate config is still loaded.
     */
    public Map<Location, Crate> getCrateLocations() {
        Map<Location, Crate> result = new LinkedHashMap<>();
        for (CrateLocation cl : crateLocations.values()) {
            Crate c = getCrate(cl.getCrateName());
            if (c != null) result.put(cl.getLocation(), c);
        }
        return Collections.unmodifiableMap(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a unique 6-character hexadecimal placement ID that does not
     * collide with any currently registered placement.
     */
    private String generatePlacementId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        } while (crateLocations.containsKey(id));
        return id;
    }

    // -------------------------------------------------------------------------
    // Errors
    // -------------------------------------------------------------------------

    public List<String> getErrors() { return Collections.unmodifiableList(errors); }

    public void addError(String error) { errors.add(error); }

    public void clearErrors() { errors.clear(); }
}
