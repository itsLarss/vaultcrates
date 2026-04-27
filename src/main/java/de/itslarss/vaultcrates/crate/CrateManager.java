package de.itslarss.vaultcrates.crate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all loaded crate configurations and their physical world placements.
 *
 * <p>Crate YAML files live in {@code plugins/VaultCrates/crates/}.
 * Placed crate instances are persisted in {@code data/locations.json}.</p>
 *
 * <h3>Placement types</h3>
 * <ul>
 *   <li><b>Block crate</b> — a vanilla block (e.g. ENDER_CHEST) registered via
 *       {@code BlockPlaceEvent}. Looked up by block {@link Location}.</li>
 *   <li><b>Furniture crate</b> — an ItemsAdder furniture {@link ArmorStand} entity.
 *       The entity is tagged with the {@code vc_crate_name} PDC key and looked up
 *       by entity {@link UUID}. The placement location is still stored for the
 *       hologram and {@code /vc placements} display.</li>
 * </ul>
 *
 * <h3>Pending furniture placements</h3>
 * When a player right-clicks with a furniture crate item, {@code CrateListener}
 * registers a <em>pending</em> entry via {@link #addPendingFurniture}. A 1-tick
 * delayed task then resolves the newly spawned ArmorStand via
 * {@link #resolvePendingFurniture}.
 */
public class CrateManager {

    // -------------------------------------------------------------------------
    // PDC key shared with CrateListener
    // -------------------------------------------------------------------------

    /** PDC key written on every placed crate entity and crate block-item. */
    public static final String PDC_CRATE_NAME = "vc_crate_name";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final VaultCrates plugin;
    private final File cratesFolder;

    /** Map of crate-name (lower-case) → Crate. */
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    /**
     * Primary placement store: placement-ID → CrateLocation.
     * Contains both block and furniture placements.
     */
    private final Map<String, CrateLocation> crateLocations = new LinkedHashMap<>();

    /**
     * Runtime index for fast entity-based lookup: entity UUID → placement-ID.
     * Rebuilt from {@link #crateLocations} on every {@link #reload()} and updated
     * whenever furniture crates are placed or removed.
     */
    private final Map<UUID, String> entityIndex = new HashMap<>();

    /**
     * Temporary map of player UUID → pending furniture info.
     * Written by {@link #addPendingFurniture} and consumed by
     * {@link #resolvePendingFurniture}.
     */
    private final Map<UUID, PendingFurniture> pendingFurniture = new HashMap<>();

    /** Config/parse errors collected during the last reload. */
    private final List<String> errors = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Inner record: PendingFurniture
    // -------------------------------------------------------------------------

    /**
     * Holds the data needed to register a furniture crate once its ArmorStand
     * entity has been spawned by ItemsAdder.
     */
    public record PendingFurniture(String crateName, Location targetLocation) {}

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

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
        entityIndex.clear();
        pendingFurniture.clear();

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

        // Rebuild entity index (try to resolve furniture entities that are loaded)
        rebuildEntityIndex();

        plugin.getLogger().info("Loaded " + crates.size() + " crate(s), "
                + crateLocations.size() + " placement(s) ("
                + entityIndex.size() + " furniture entity/entities resolved).");
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

            // Furniture crate — entity UUID stored alongside location
            UUID entityUuid = null;
            if (obj.has("entityUUID")) {
                try { entityUuid = UUID.fromString(obj.get("entityUUID").getAsString()); }
                catch (IllegalArgumentException ignored) { /* malformed UUID */ }
            }

            CrateLocation cl = CrateLocation.deserialize(id, locStr, crateName, entityUuid);
            if (cl != null) {
                crateLocations.put(id, cl);
            } else {
                plugin.getLogger().warning("Could not load crate placement '" + id
                        + "' (world not loaded yet?). It will be restored on the next reload.");
            }
        }
    }

    /** Persists all current placements to {@code data/locations.json}. */
    public void saveCrateLocations() {
        JsonObject placements = new JsonObject();
        for (Map.Entry<String, CrateLocation> entry : crateLocations.entrySet()) {
            CrateLocation cl = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("crate",    cl.getCrateName());
            obj.addProperty("location", cl.serialize());
            if (cl.isFurnitureCrate()) {
                obj.addProperty("entityUUID", cl.getEntityUuid().toString());
            }
            placements.add(entry.getKey(), obj);
        }
        JsonObject root = new JsonObject();
        root.add("placements", placements);
        plugin.getConfigManager().saveLocationsJson(root);
    }

    // -------------------------------------------------------------------------
    // Entity index
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the runtime entity index from all furniture placements that have a
     * stored UUID. For each UUID, attempts to find the entity in any loaded world;
     * if found, verifies the PDC tag and adds it to the index.
     *
     * <p>This is called after every {@link #reload()} so that already-spawned
     * furniture entities are re-associated after a plugin reload.</p>
     */
    public void rebuildEntityIndex() {
        entityIndex.clear();
        NamespacedKey crateKey = new NamespacedKey(plugin, PDC_CRATE_NAME);

        for (Map.Entry<String, CrateLocation> entry : crateLocations.entrySet()) {
            CrateLocation cl = entry.getValue();
            if (!cl.isFurnitureCrate()) continue;

            UUID uuid = cl.getEntityUuid();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) {
                // Entity may be in an unloaded chunk — will be picked up by ChunkLoadEvent
                continue;
            }
            // Verify our PDC tag is still on the entity; re-apply if lost
            if (!entity.getPersistentDataContainer().has(crateKey, PersistentDataType.STRING)) {
                entity.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, cl.getCrateName());
            }
            entityIndex.put(uuid, entry.getKey());
        }
    }

    /**
     * Indexes a single furniture entity that became loaded (e.g. on chunk load).
     * Called by {@code CrateListener}'s {@code ChunkLoadEvent} handler.
     *
     * @param entity the entity to check
     */
    public void indexEntityIfCrate(Entity entity) {
        if (!(entity instanceof ArmorStand)) return;
        NamespacedKey crateKey = new NamespacedKey(plugin, PDC_CRATE_NAME);
        if (!entity.getPersistentDataContainer().has(crateKey, PersistentDataType.STRING)) return;

        UUID uuid = entity.getUniqueId();
        // Find the matching placement by UUID
        for (Map.Entry<String, CrateLocation> entry : crateLocations.entrySet()) {
            if (uuid.equals(entry.getValue().getEntityUuid())) {
                entityIndex.put(uuid, entry.getKey());
                return;
            }
        }
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
    // Placement management — block crates
    // -------------------------------------------------------------------------

    /**
     * Returns the crate placed at the given block location, or {@code null}.
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
     * Registers a new block-crate placement, persists to disk, and returns the
     * generated placement ID.
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
     */
    public void removeCrateAt(Location loc) {
        String target = LocationUtil.serialize(loc.getBlock().getLocation());
        crateLocations.entrySet().removeIf(e -> e.getValue().serialize().equals(target));
        saveCrateLocations();
    }

    // -------------------------------------------------------------------------
    // Placement management — furniture (entity) crates
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link Crate} whose furniture entity has the given UUID,
     * or {@code null} if not registered.
     */
    @Nullable
    public Crate getCrateAtEntity(UUID entityUuid) {
        String placementId = entityIndex.get(entityUuid);
        if (placementId == null) return null;
        CrateLocation cl = crateLocations.get(placementId);
        return cl == null ? null : getCrate(cl.getCrateName());
    }

    /**
     * Returns the {@link CrateLocation} for the furniture entity with the given UUID,
     * or {@code null}.
     */
    @Nullable
    public CrateLocation getCrateLocationAtEntity(UUID entityUuid) {
        String placementId = entityIndex.get(entityUuid);
        return placementId == null ? null : crateLocations.get(placementId);
    }

    /**
     * Registers a furniture crate entity, tags it with the {@code vc_crate_name}
     * PDC key, persists to disk, and returns the generated placement ID.
     *
     * @param entity    the ArmorStand spawned by ItemsAdder
     * @param crate     the crate this entity represents
     * @param spawnLoc  the world location used for the hologram
     * @return the unique placement ID
     */
    public String setCrateAtEntity(Entity entity, Crate crate, Location spawnLoc) {
        NamespacedKey crateKey = new NamespacedKey(plugin, PDC_CRATE_NAME);
        entity.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, crate.getName());

        String id = generatePlacementId();
        UUID uuid = entity.getUniqueId();

        crateLocations.put(id, new CrateLocation(id, spawnLoc.getBlock().getLocation(),
                crate.getName(), uuid));
        entityIndex.put(uuid, id);
        saveCrateLocations();
        return id;
    }

    /**
     * Removes the furniture crate registration for the given entity UUID,
     * and persists to disk.
     *
     * @param entityUuid the entity's UUID
     * @return {@code true} if a placement was found and removed
     */
    public boolean removeCrateAtEntity(UUID entityUuid) {
        String placementId = entityIndex.remove(entityUuid);
        if (placementId == null) return false;
        crateLocations.remove(placementId);
        saveCrateLocations();
        return true;
    }

    // -------------------------------------------------------------------------
    // Pending furniture placement
    // -------------------------------------------------------------------------

    /**
     * Records a pending furniture placement initiated by the given player.
     * The entry is consumed (and the furniture ArmorStand registered) by
     * {@link #resolvePendingFurniture} one tick later.
     *
     * @param playerUuid   the placing player's UUID
     * @param crateName    the crate type being placed
     * @param targetLoc    the expected spawn location (clicked block + face)
     */
    public void addPendingFurniture(UUID playerUuid, String crateName, Location targetLoc) {
        pendingFurniture.put(playerUuid, new PendingFurniture(crateName, targetLoc));
    }

    /**
     * Attempts to resolve a pending furniture placement for the given player by
     * scanning for a new, unregistered {@link ArmorStand} within 2 blocks of the
     * expected location.  If found, the entity is registered as a crate and the
     * hologram is spawned.
     *
     * <p>Should be called 1 tick after {@link #addPendingFurniture}.</p>
     *
     * @param playerUuid the placing player's UUID
     * @return the placement ID if successful, or {@code null} if no entity was found
     */
    @Nullable
    public String resolvePendingFurniture(UUID playerUuid) {
        PendingFurniture pending = pendingFurniture.remove(playerUuid);
        if (pending == null) return null;

        Crate crate = getCrate(pending.crateName());
        if (crate == null) return null;

        Location target = pending.targetLocation();
        NamespacedKey crateKey = new NamespacedKey(plugin, PDC_CRATE_NAME);

        // Search for a nearby ArmorStand that is NOT already registered as a crate
        Optional<Entity> found = target.getWorld()
                .getNearbyEntities(target.clone().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0).stream()
                .filter(e -> e instanceof ArmorStand)
                .filter(e -> !e.getPersistentDataContainer().has(crateKey, PersistentDataType.STRING))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(target)));

        if (found.isEmpty()) return null;

        Entity entity = found.get();
        String id = setCrateAtEntity(entity, crate, entity.getLocation());

        // Spawn hologram above the entity
        if (!crate.getHologramLines().isEmpty()) {
            plugin.getHologramManager().spawnHologram(
                    plugin.getHologramManager().locationToId(entity.getLocation()),
                    entity.getLocation(), crate.getHologramLines());
        }

        return id;
    }

    /**
     * Returns the pending furniture data for the given player, or {@code null}.
     */
    @Nullable
    public PendingFurniture getPendingFurniture(UUID playerUuid) {
        return pendingFurniture.get(playerUuid);
    }

    // -------------------------------------------------------------------------
    // Shared placement management
    // -------------------------------------------------------------------------

    /**
     * Removes a crate placement by its unique placement ID.
     * Handles both block and furniture placements.
     * Called by {@code /vc remove <id>}.
     *
     * @param id the placement ID
     * @return {@code true} if a placement was found and removed
     */
    public boolean removePlacement(String id) {
        CrateLocation cl = crateLocations.remove(id);
        if (cl == null) return false;
        if (cl.isFurnitureCrate()) {
            entityIndex.remove(cl.getEntityUuid());
        }
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

    /**
     * Returns {@code true} if the placed crate at {@code cl} still physically
     * exists in the world — i.e. the block is non-air (block crate) or the
     * ArmorStand entity is still loaded (furniture crate).
     *
     * Used by the proximity check to skip stale JSON entries whose blocks were
     * removed without using {@code /vc break}.
     */
    public boolean isStillPlaced(CrateLocation cl) {
        if (cl.isFurnitureCrate()) {
            java.util.UUID uuid = cl.getEntityUuid();
            return uuid != null && org.bukkit.Bukkit.getEntity(uuid) != null;
        }
        Location loc = cl.getLocation();
        return loc.getWorld() != null && loc.getBlock().getType() != org.bukkit.Material.AIR;
    }

    // -------------------------------------------------------------------------
    // Errors
    // -------------------------------------------------------------------------

    public List<String> getErrors() { return Collections.unmodifiableList(errors); }

    public void addError(String error) { errors.add(error); }

    public void clearErrors() { errors.clear(); }

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
}
