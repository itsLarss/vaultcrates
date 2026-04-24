package de.itslarss.vaultcrates.animation;

import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Holds all state for a single crate-opening animation session.
 * Tracks spawned entities, scheduled tasks and the selected rewards.
 */
public class AnimationSession {

    private final UUID playerUUID;
    private final Crate crate;
    private final Location crateLocation;

    private List<Reward> selectedRewards = new ArrayList<>();
    private Reward bestReward;

    private final long startTime = System.currentTimeMillis();
    private boolean finished = false;
    private int tickCount = 0;

    /** Entities spawned during this animation (ItemDisplay, TextDisplay, etc.). */
    private final List<Entity> spawnedEntities = new ArrayList<>();

    /** Tasks scheduled during this animation. */
    private final List<BukkitTask> tasks = new ArrayList<>();

    /** Arbitrary animation-specific data storage. */
    private final Map<String, Object> data = new HashMap<>();

    public AnimationSession(Player player, Crate crate, Location crateLocation) {
        this.playerUUID = player.getUniqueId();
        this.crate = crate;
        this.crateLocation = crateLocation.getBlock().getLocation().clone();
    }

    // -------------------------------------------------------------------------
    // Player access
    // -------------------------------------------------------------------------

    /** Returns the player if they are still online, otherwise {@code null}. */
    public Player getPlayer() { return Bukkit.getPlayer(playerUUID); }

    public UUID getPlayerUUID() { return playerUUID; }

    // -------------------------------------------------------------------------
    // Entity & task tracking
    // -------------------------------------------------------------------------

    /** Registers a spawned entity so it can be removed on cleanup. */
    public void addEntity(Entity entity) { if (entity != null) spawnedEntities.add(entity); }

    /** Registers a scheduled task so it can be cancelled on cleanup. */
    public void addTask(BukkitTask task) { if (task != null) tasks.add(task); }

    /**
     * Cancels all scheduled tasks and removes all spawned entities.
     * Should be called when the animation finishes or is force-stopped.
     */
    public void cleanup() {
        tasks.forEach(t -> { try { t.cancel(); } catch (Exception ignored) {} });
        tasks.clear();
        spawnedEntities.forEach(e -> { try { if (e != null && e.isValid()) e.remove(); } catch (Exception ignored) {} });
        spawnedEntities.clear();
    }

    // -------------------------------------------------------------------------
    // Arbitrary data store (for animation implementations)
    // -------------------------------------------------------------------------

    public void putData(String key, Object value) { data.put(key, value); }

    public Object getData(String key) { return data.get(key); }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) { return type.cast(data.get(key)); }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public Crate getCrate() { return crate; }
    public Location getCrateLocation() { return crateLocation.clone(); }
    public List<Reward> getSelectedRewards() { return selectedRewards; }
    public void setSelectedRewards(List<Reward> rewards) { this.selectedRewards = new ArrayList<>(rewards); }
    public Reward getBestReward() { return bestReward; }
    public void setBestReward(Reward bestReward) { this.bestReward = bestReward; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public int getTickCount() { return tickCount; }
    public void incrementTick() { tickCount++; }
    public long getElapsedMs() { return System.currentTimeMillis() - startTime; }
    public long getStartTime() { return startTime; }
    public List<Entity> getSpawnedEntities() { return Collections.unmodifiableList(spawnedEntities); }
}
