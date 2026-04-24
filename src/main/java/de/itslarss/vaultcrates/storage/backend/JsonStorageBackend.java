package de.itslarss.vaultcrates.storage.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.itslarss.vaultcrates.VaultCrates;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * JSON-file-based storage backend.
 * All data is kept in memory and flushed to {@code plugins/VaultCrates/data/data.json}.
 */
public class JsonStorageBackend implements StorageBackend {

    // -------------------------------------------------------------------------
    // Inner data classes
    // -------------------------------------------------------------------------

    static class PlayerData {
        Map<String, Integer> virtualKeys                      = new HashMap<>(); // crate → count
        Map<String, Map<String, Integer>> pity                = new HashMap<>(); // crate → rarity → count
        Map<String, Map<String, Integer>> rewardLimits        = new HashMap<>(); // crate → reward → count
        Map<String, Integer> openCounts                       = new HashMap<>(); // crate → count
        Set<String> claimedMilestones                         = new HashSet<>(); // "crate:milestoneId"
    }

    static class GlobalData {
        Map<String, Map<String, Integer>> rewardLimitsGlobal  = new HashMap<>(); // crate → reward → count
        Map<String, Long> usedKeys                            = new HashMap<>(); // keyUUID → unix-seconds
    }

    static class StorageData {
        Map<String, PlayerData> players = new HashMap<>(); // uuid-string → PlayerData
        GlobalData global               = new GlobalData();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final VaultCrates plugin;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private StorageData storageData = new StorageData();

    public JsonStorageBackend(VaultCrates plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "data.json");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init() {
        if (!dataFile.exists()) {
            storageData = new StorageData();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            StorageData loaded = gson.fromJson(reader, StorageData.class);
            if (loaded != null) {
                storageData = loaded;
            }
            // Ensure non-null sub-objects after deserialization
            if (storageData.players == null)          storageData.players = new HashMap<>();
            if (storageData.global == null)           storageData.global  = new GlobalData();
            if (storageData.global.rewardLimitsGlobal == null) storageData.global.rewardLimitsGlobal = new HashMap<>();
            if (storageData.global.usedKeys == null)  storageData.global.usedKeys = new HashMap<>();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load data/data.json", e);
            storageData = new StorageData();
        }
    }

    @Override
    public void saveAll() {
        try {
            File dataDir = dataFile.getParentFile();
            if (!dataDir.exists()) dataDir.mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                gson.toJson(storageData, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/data.json", e);
        }
    }

    @Override
    public void close() {
        saveAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PlayerData getOrCreatePlayer(UUID player) {
        return storageData.players.computeIfAbsent(player.toString(), k -> new PlayerData());
    }

    private PlayerData getPlayer(UUID player) {
        PlayerData pd = storageData.players.get(player.toString());
        if (pd == null) return new PlayerData(); // immutable default, not stored
        return pd;
    }

    // -------------------------------------------------------------------------
    // Virtual keys
    // -------------------------------------------------------------------------

    @Override
    public int getVirtualKeys(UUID player, String crate) {
        PlayerData pd = getPlayer(player);
        if (pd.virtualKeys == null) return 0;
        return pd.virtualKeys.getOrDefault(crate.toLowerCase(), 0);
    }

    @Override
    public void setVirtualKeys(UUID player, String crate, int amount) {
        PlayerData pd = getOrCreatePlayer(player);
        if (pd.virtualKeys == null) pd.virtualKeys = new HashMap<>();
        String key = crate.toLowerCase();
        if (amount <= 0) {
            pd.virtualKeys.remove(key);
        } else {
            pd.virtualKeys.put(key, amount);
        }
    }

    @Override
    public void addVirtualKeys(UUID player, String crate, int amount) {
        setVirtualKeys(player, crate, getVirtualKeys(player, crate) + amount);
    }

    @Override
    public boolean removeVirtualKeys(UUID player, String crate, int amount) {
        int current = getVirtualKeys(player, crate);
        if (current < amount) return false;
        setVirtualKeys(player, crate, current - amount);
        return true;
    }

    /**
     * Returns all crate → key-count pairs for a player (in-memory only).
     * Called by {@code StorageManager.VirtualKeyStorageAdapter#getAllKeys}.
     */
    public java.util.Map<String, Integer> getAllVirtualKeys(UUID player) {
        PlayerData pd = storageData.players.get(player.toString());
        if (pd == null || pd.virtualKeys == null) return java.util.Collections.emptyMap();
        return java.util.Collections.unmodifiableMap(pd.virtualKeys);
    }

    // -------------------------------------------------------------------------
    // Pity
    // -------------------------------------------------------------------------

    @Override
    public int getPity(UUID player, String crate, String rarityId) {
        PlayerData pd = getPlayer(player);
        if (pd.pity == null) return 0;
        Map<String, Integer> rarityMap = pd.pity.get(crate);
        if (rarityMap == null) return 0;
        return rarityMap.getOrDefault(rarityId, 0);
    }

    @Override
    public void incrementPity(UUID player, String crate, String rarityId) {
        PlayerData pd = getOrCreatePlayer(player);
        if (pd.pity == null) pd.pity = new HashMap<>();
        int current = getPity(player, crate, rarityId);
        pd.pity.computeIfAbsent(crate, k -> new HashMap<>()).put(rarityId, current + 1);
    }

    @Override
    public void resetPity(UUID player, String crate, String rarityId) {
        PlayerData pd = storageData.players.get(player.toString());
        if (pd == null || pd.pity == null) return;
        Map<String, Integer> rarityMap = pd.pity.get(crate);
        if (rarityMap == null) return;
        rarityMap.remove(rarityId);
        if (rarityMap.isEmpty()) pd.pity.remove(crate);
    }

    // -------------------------------------------------------------------------
    // Reward limits
    // -------------------------------------------------------------------------

    @Override
    public int getGlobalLimitCount(String crate, String rewardId) {
        if (storageData.global.rewardLimitsGlobal == null) return 0;
        Map<String, Integer> rewardMap = storageData.global.rewardLimitsGlobal.get(crate);
        if (rewardMap == null) return 0;
        return rewardMap.getOrDefault(rewardId, 0);
    }

    @Override
    public void incrementGlobalLimit(String crate, String rewardId) {
        if (storageData.global.rewardLimitsGlobal == null) storageData.global.rewardLimitsGlobal = new HashMap<>();
        int current = getGlobalLimitCount(crate, rewardId);
        storageData.global.rewardLimitsGlobal.computeIfAbsent(crate, k -> new HashMap<>()).put(rewardId, current + 1);
    }

    @Override
    public int getPlayerLimitCount(UUID player, String crate, String rewardId) {
        PlayerData pd = getPlayer(player);
        if (pd.rewardLimits == null) return 0;
        Map<String, Integer> rewardMap = pd.rewardLimits.get(crate);
        if (rewardMap == null) return 0;
        return rewardMap.getOrDefault(rewardId, 0);
    }

    @Override
    public void incrementPlayerLimit(UUID player, String crate, String rewardId) {
        PlayerData pd = getOrCreatePlayer(player);
        if (pd.rewardLimits == null) pd.rewardLimits = new HashMap<>();
        int current = getPlayerLimitCount(player, crate, rewardId);
        pd.rewardLimits.computeIfAbsent(crate, k -> new HashMap<>()).put(rewardId, current + 1);
    }

    // -------------------------------------------------------------------------
    // Milestone open counts
    // -------------------------------------------------------------------------

    @Override
    public int getOpenCount(UUID player, String crate) {
        PlayerData pd = getPlayer(player);
        if (pd.openCounts == null) return 0;
        return pd.openCounts.getOrDefault(crate, 0);
    }

    @Override
    public int incrementOpenCount(UUID player, String crate) {
        PlayerData pd = getOrCreatePlayer(player);
        if (pd.openCounts == null) pd.openCounts = new HashMap<>();
        int newCount = getOpenCount(player, crate) + 1;
        pd.openCounts.put(crate, newCount);
        return newCount;
    }

    @Override
    public boolean isMilestoneClaimed(UUID player, String crate, String milestoneId) {
        PlayerData pd = getPlayer(player);
        if (pd.claimedMilestones == null) return false;
        return pd.claimedMilestones.contains(crate + ":" + milestoneId);
    }

    @Override
    public void setMilestoneClaimed(UUID player, String crate, String milestoneId) {
        PlayerData pd = getOrCreatePlayer(player);
        if (pd.claimedMilestones == null) pd.claimedMilestones = new HashSet<>();
        pd.claimedMilestones.add(crate + ":" + milestoneId);
    }

    // -------------------------------------------------------------------------
    // Used key UUIDs (anti-dupe)
    // -------------------------------------------------------------------------

    @Override
    public boolean isKeyUsed(UUID keyUuid) {
        if (storageData.global.usedKeys == null) return false;
        return storageData.global.usedKeys.containsKey(keyUuid.toString());
    }

    @Override
    public void markKeyUsed(UUID keyUuid) {
        if (storageData.global.usedKeys == null) storageData.global.usedKeys = new HashMap<>();
        storageData.global.usedKeys.put(keyUuid.toString(), System.currentTimeMillis() / 1000L);
    }

    @Override
    public void pruneOldKeys(int expiryDays) {
        if (expiryDays <= 0) return;
        if (storageData.global.usedKeys == null) return;
        long cutoff = System.currentTimeMillis() / 1000L - (long) expiryDays * 86400L;
        storageData.global.usedKeys.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
