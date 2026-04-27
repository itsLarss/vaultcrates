package de.itslarss.vaultcrates.crate;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.AnimationVisibility;
import de.itslarss.vaultcrates.crate.reward.Rarity;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents a configured crate type with all its settings, rewards and key configuration.
 * Loaded from a YAML file in the crates/ directory.
 */
public class Crate {

    // -------------------------------------------------------------------------
    // Inner class: KeyConfig
    // -------------------------------------------------------------------------

    /** Configuration for the physical key required to open this crate. */
    public static class KeyConfig {
        private boolean require;
        private int keysRequired;
        private boolean matchName;
        private boolean matchLore;
        private boolean matchEnchants;
        private boolean matchNBT;
        private Material material;
        private String name;
        private boolean enchanted;
        private List<String> lores;
        private String iaKeyModel;   // Optional ItemsAdder key model (e.g. "namespace:key_id")
        private String oraxenKeyModel;
        private String nexoKeyModel;
        private double shopPrice;    // 0 = not in shop
        private boolean shopEnabled;

        public boolean isRequire() { return require; }
        public int getKeysRequired() { return keysRequired; }
        public boolean isMatchName() { return matchName; }
        public boolean isMatchLore() { return matchLore; }
        public boolean isMatchEnchants() { return matchEnchants; }
        public boolean isMatchNBT() { return matchNBT; }
        public Material getMaterial() { return material; }
        public String getName() { return name; }
        public boolean isEnchanted() { return enchanted; }
        public List<String> getLores() { return lores == null ? List.of() : Collections.unmodifiableList(lores); }
        public String getIaKeyModel()     { return iaKeyModel; }
        public String getOraxenKeyModel() { return oraxenKeyModel; }
        public String getNexoKeyModel()   { return nexoKeyModel; }
        public double getShopPrice()      { return shopPrice; }
        public boolean isShopEnabled()    { return shopEnabled; }

        // Setters (used by in-game key editor)
        public void setMaterial(Material material) { this.material = material; }
        public void setName(String name) { this.name = name; }
        public void setEnchanted(boolean enchanted) { this.enchanted = enchanted; }
        public void setMatchNBT(boolean matchNBT) { this.matchNBT = matchNBT; }
        public void setMatchName(boolean matchName) { this.matchName = matchName; }
        public void setMatchLore(boolean matchLore) { this.matchLore = matchLore; }

        public static KeyConfig fromConfig(ConfigurationSection sec) {
            KeyConfig kc = new KeyConfig();
            if (sec == null) {
                kc.require = false;
                kc.keysRequired = 1;
                kc.material = Material.TRIPWIRE_HOOK;
                kc.name = "&aKey";
                kc.lores = new ArrayList<>();
                return kc;
            }
            kc.require = sec.getBoolean("Require", false);
            kc.keysRequired = Math.max(1, sec.getInt("KeysRequired", 1));
            kc.matchName = sec.getBoolean("MatchName", true);
            kc.matchLore = sec.getBoolean("MatchLore", false);
            kc.matchEnchants = sec.getBoolean("MatchEnchants", false);
            kc.matchNBT = sec.getBoolean("MatchNBT", true);
            String matStr = sec.getString("Material", "TRIPWIRE_HOOK");
            try { kc.material = Material.valueOf(matStr.toUpperCase()); }
            catch (IllegalArgumentException e) { kc.material = Material.TRIPWIRE_HOOK; }
            kc.name = sec.getString("Name", "&aKey");
            kc.enchanted = sec.getBoolean("Enchanted", false);
            kc.lores = sec.getStringList("Lores");
            kc.iaKeyModel     = sec.getString("IAModel",     null);
            kc.oraxenKeyModel = sec.getString("OraxenModel", null);
            kc.nexoKeyModel   = sec.getString("NexoModel",   null);
            kc.shopPrice   = sec.getDouble("ShopPrice", 0.0);
            kc.shopEnabled = sec.getBoolean("ShopEnabled", false);
            return kc;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String name;
    private final File configFile;

    private Material material;
    private boolean enchanted;
    private boolean onlyPlaceable;
    private boolean rewardsDontRepeat;
    private boolean shiftInstantlyOpen;
    private boolean shiftInstantlyOpenAll;
    private int customModelData;
    private int size;
    private int previewSize;
    private int proximityDistance;

    private AnimationType animationType;
    private AnimationVisibility visibility;

    private List<String> hologramLines;
    private List<String> rewardHologramLines;
    private List<String> lores;
    private List<String> finalMessage;
    private List<String> commandsOnPlace;
    private List<String> closedCrateText;

    private boolean previewEnabled;
    private boolean showChanceInPreview;
    private String previewTitle;

    private KeyConfig keyConfig;
    private List<Reward> prizes;
    private List<Reward> bestPrizes;
    private List<String> onlyPlaceableLocations;

    private String middleAnimationItem;
    private int middleAnimationItemCustomModelData;
    private String iaBlock;      // Optional ItemsAdder model for the crate block item
    private String oraxenBlock;  // Optional Oraxen model for the crate block item
    private String nexoBlock;    // Optional Nexo model for the crate block item

    // Economy opening (requires Vault)
    private boolean useEconomy;
    private double economyPrice;

    // Weight-based prize selection (alternative to Chance:)
    private boolean useWeight;

    // Selectable rewards
    private boolean selectableRewards;
    private int selectableRewardsCount;

    // Milestones
    private List<Milestone> milestones;

    // Pushback when requirements not met
    private boolean pushbackEnabled;
    private double pushbackStrength;

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private Crate(String name, File configFile) {
        this.name = name;
        this.configFile = configFile;
        this.prizes = new ArrayList<>();
        this.bestPrizes = new ArrayList<>();
        this.hologramLines = new ArrayList<>();
        this.rewardHologramLines = new ArrayList<>();
        this.lores = new ArrayList<>();
        this.finalMessage = new ArrayList<>();
        this.commandsOnPlace = new ArrayList<>();
        this.closedCrateText = new ArrayList<>();
        this.onlyPlaceableLocations = new ArrayList<>();
        this.milestones = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Static factory — load from YAML file
    // -------------------------------------------------------------------------

    /**
     * Loads a crate from its YAML config file.
     *
     * @param fileName the name without extension (used as crate ID)
     * @param file     the actual config file
     * @return the parsed {@link Crate}
     */
    public static Crate fromConfig(String fileName, File file) {
        String name = fileName.replace(".yml", "");
        Crate crate = new Crate(name, file);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Basic settings — support both "Material:" and "Block:" keys
        String matStr = cfg.getString("Material");
        if (matStr == null) matStr = cfg.getString("Block", "CHEST");
        try { crate.material = Material.valueOf(matStr.toUpperCase()); }
        catch (IllegalArgumentException e) { crate.material = Material.CHEST; }

        crate.enchanted = cfg.getBoolean("Enchanted", false);
        crate.onlyPlaceable = cfg.getBoolean("OnlyPlaceable", true);
        crate.rewardsDontRepeat = cfg.getBoolean("RewardsDontRepeat", false);
        crate.shiftInstantlyOpen = cfg.getBoolean("ShiftInstantlyOpen", false);
        crate.shiftInstantlyOpenAll = cfg.getBoolean("ShiftInstantlyOpenAll", false);
        crate.customModelData = cfg.getInt("CustomModelData", 0);
        // Size defaults to 1 — most animations show exactly one reward.
        // Only set this higher for multi-slot animations like DISPLAY.
        crate.size = Math.max(1, cfg.getInt("Size", 1));
        crate.previewSize = cfg.getInt("PreviewSize", 54);
        crate.proximityDistance = cfg.getInt("Proximity_Distance", 8);
        crate.previewEnabled = cfg.getBoolean("PreviewEnabled", true);
        crate.showChanceInPreview = cfg.getBoolean("ShowChanceInPreview", true);
        crate.previewTitle = cfg.getString("PreviewTitle", "&e&lRewards Preview");

        // Animation — support both "AnimationVisibilityOption:" and "AnimationVisibility:" keys
        crate.animationType = AnimationType.fromString(cfg.getString("Animation", "ROUND"));
        String visStr = cfg.getString("AnimationVisibilityOption");
        if (visStr == null) visStr = cfg.getString("AnimationVisibility", "PROXIMITY");
        crate.visibility = AnimationVisibility.fromString(visStr);

        // Text lists
        crate.hologramLines = cfg.getStringList("HologramLines");
        if (crate.hologramLines.isEmpty()) {
            // Try ClosedCrateText as hologram fallback
            crate.hologramLines = cfg.getStringList("ClosedCrateText");
        }
        crate.rewardHologramLines = cfg.getStringList("RewardHologram");
        crate.lores = cfg.getStringList("Lores");
        crate.finalMessage = cfg.getStringList("Final_Message");
        if (crate.finalMessage.isEmpty()) crate.finalMessage = cfg.getStringList("FinalMessage");
        crate.commandsOnPlace = cfg.getStringList("CommandOnCratePlace");
        crate.closedCrateText = cfg.getStringList("ClosedCrateText");
        crate.onlyPlaceableLocations = cfg.getStringList("OnlyPlaceableAtLocations");

        // Cosmic middle item
        crate.middleAnimationItem = cfg.getString("MiddleAnimationItem", "ENDER_CHEST");
        crate.middleAnimationItemCustomModelData = cfg.getInt("MiddleAnimationItemCustomModelData", 0);
        crate.iaBlock      = cfg.getString("IAModel",     null);
        crate.oraxenBlock  = cfg.getString("OraxenModel", null);
        crate.nexoBlock    = cfg.getString("NexoModel",   null);

        // Economy opening
        crate.useEconomy   = cfg.getBoolean("UseEconomy",   false);
        crate.economyPrice = cfg.getDouble("EconomyPrice",  0.0);

        // Weight system
        crate.useWeight = cfg.getBoolean("UseWeight", false);

        // Selectable rewards
        crate.selectableRewards      = cfg.getBoolean("SelectableRewards",      false);
        crate.selectableRewardsCount = cfg.getInt("SelectableRewardsCount", 3);

        // Pushback
        ConfigurationSection pushbackSec = cfg.getConfigurationSection("Pushback");
        if (pushbackSec != null) {
            crate.pushbackEnabled  = pushbackSec.getBoolean("Enabled",  false);
            crate.pushbackStrength = pushbackSec.getDouble("Strength",  1.5);
        }

        // Milestones
        ConfigurationSection milestonesSec = cfg.getConfigurationSection("Milestones");
        if (milestonesSec != null) {
            for (String mKey : milestonesSec.getKeys(false)) {
                ConfigurationSection mSec = milestonesSec.getConfigurationSection(mKey);
                if (mSec != null) crate.milestones.add(Milestone.fromConfig(mKey, mSec));
            }
        }

        // Key config — support both "KeyCrate:" and "Key:" section names
        ConfigurationSection keySection = cfg.getConfigurationSection("KeyCrate");
        if (keySection == null) keySection = cfg.getConfigurationSection("Key");
        crate.keyConfig = KeyConfig.fromConfig(keySection);

        // Prizes — support both "Prizes:" and "Rewards:" section names
        ConfigurationSection prizesSection = cfg.getConfigurationSection("Prizes");
        if (prizesSection == null) prizesSection = cfg.getConfigurationSection("Rewards");
        if (prizesSection != null) {
            for (String key : prizesSection.getKeys(false)) {
                ConfigurationSection rewardSection = prizesSection.getConfigurationSection(key);
                if (rewardSection != null) {
                    crate.prizes.add(Reward.fromConfig(key, rewardSection));
                }
            }
        }

        // Best prizes
        ConfigurationSection bestSection = cfg.getConfigurationSection("BestPrizes");
        if (bestSection != null) {
            for (String key : bestSection.getKeys(false)) {
                ConfigurationSection rewardSection = bestSection.getConfigurationSection(key);
                if (rewardSection != null) {
                    crate.bestPrizes.add(Reward.fromConfig(key, rewardSection));
                }
            }
        }

        return crate;
    }

    // -------------------------------------------------------------------------
    // Reward selection
    // -------------------------------------------------------------------------

    /**
     * Picks a single prize from the pool using weighted random selection.
     * Respects the player's permissions, blacklisted permissions, reward limits, and pity system.
     *
     * @param player the player opening the crate
     * @return a randomly selected {@link Reward}, or null if no eligible prizes
     */
    public Reward pickPrize(Player player) {
        return pickPrize(player, new HashSet<>());
    }

    /**
     * Picks a prize excluding already-selected IDs (for {@link #rewardsDontRepeat}).
     */
    public Reward pickPrize(Player player, Set<String> alreadyPicked) {
        List<Reward> eligible = new ArrayList<>();
        for (Reward r : prizes) {
            if (r.canReceive(player) && (!rewardsDontRepeat || !alreadyPicked.contains(r.getId()))) {
                eligible.add(r);
            }
        }
        if (eligible.isEmpty()) {
            // Fall back to all prizes if filtering leaves nothing
            eligible = new ArrayList<>(prizes);
        }
        if (eligible.isEmpty()) return null;

        // ── Reward limit filtering ────────────────────────────────────────────
        // Remove rewards that have hit their global or per-player cap.
        // Only apply limits when the filtered list stays non-empty.
        var limitStorage = VaultCrates.getInstance()
                .getStorageManager().getRewardLimitStorage();
        List<Reward> limitFiltered = eligible.stream().filter(r -> {
            if (r.getGlobalLimit() > 0
                    && limitStorage.getGlobalCount(name, r.getId()) >= r.getGlobalLimit()) {
                return false;
            }
            if (r.getPlayerLimit() > 0
                    && limitStorage.getPlayerCount(player.getUniqueId(), name, r.getId()) >= r.getPlayerLimit()) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());
        if (!limitFiltered.isEmpty()) eligible = limitFiltered;

        // ── Pity check ────────────────────────────────────────────────────────
        // If a rarity's pity counter has reached its threshold, force a reward
        // of that rarity (pick highest-pity-ratio rarity first).
        var pityStorage = VaultCrates.getInstance()
                .getStorageManager().getPityStorage();
        Rarity forcedRarity = null;
        double bestRatio = 0;
        for (Rarity rarity : Rarity.getAll().values()) {
            if (rarity.getPityAfter() <= 0) continue;
            int count = pityStorage.getCount(player.getUniqueId(), name, rarity.getId());
            if (count >= rarity.getPityAfter()) {
                double ratio = (double) count / rarity.getPityAfter();
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    forcedRarity = rarity;
                }
            }
        }
        if (forcedRarity != null) {
            final String forcedRarityId = forcedRarity.getId();
            List<Reward> pityPool = eligible.stream()
                    .filter(r -> forcedRarityId.equals(r.getRarityId()))
                    .collect(Collectors.toList());
            if (!pityPool.isEmpty()) {
                return pityPool.get(ThreadLocalRandom.current().nextInt(pityPool.size()));
            }
        }

        // ── Weight-based OR chance-based weighted random selection ────────────
        if (useWeight) {
            double totalW = eligible.stream().mapToDouble(r -> Math.max(0.001, r.getWeight() > 0 ? r.getWeight() : r.getChance())).sum();
            double randW = ThreadLocalRandom.current().nextDouble() * totalW;
            double cumW = 0;
            for (Reward r : eligible) {
                cumW += Math.max(0.001, r.getWeight() > 0 ? r.getWeight() : r.getChance());
                if (randW <= cumW) return r;
            }
        } else {
            double totalWeight = eligible.stream().mapToDouble(Reward::getChance).sum();
            double rand = ThreadLocalRandom.current().nextDouble() * totalWeight;
            double cumulative = 0;
            for (Reward r : eligible) {
                cumulative += r.getChance();
                if (rand <= cumulative) return r;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Picks {@code count} prizes, optionally without repeats.
     *
     * @param player the player opening the crate
     * @param count  how many prizes to pick
     * @return an ordered list of selected prizes
     */
    public List<Reward> pickPrizes(Player player, int count) {
        List<Reward> result = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Reward r = pickPrize(player, picked);
            if (r != null) {
                result.add(r);
                picked.add(r.getId());
            }
        }
        return result;
    }

    /**
     * Picks a best prize (from the {@code BestPrizes} section) using weighted random.
     *
     * @param player the player opening the crate
     * @return the selected best prize, or null if none configured
     */
    public Reward pickBestPrize(Player player) {
        if (bestPrizes.isEmpty()) return null;

        List<Reward> eligible = new ArrayList<>();
        for (Reward r : bestPrizes) {
            if (r.canReceive(player)) eligible.add(r);
        }
        if (eligible.isEmpty()) eligible = new ArrayList<>(bestPrizes);
        if (eligible.isEmpty()) return null;

        double totalWeight = eligible.stream().mapToDouble(Reward::getChance).sum();
        double rand = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (Reward r : eligible) {
            cumulative += r.getChance();
            if (rand <= cumulative) return r;
        }
        return eligible.get(eligible.size() - 1);
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    /**
     * Builds the physical key item for this crate.
     *
     * @param amount the stack size
     * @return the key {@link ItemStack}
     */
    public ItemStack buildKeyItem(int amount) {
        return ItemBuilder.of(keyConfig.getMaterial())
                .name(keyConfig.getName())
                .lore(keyConfig.getLores())
                .amount(amount)
                .glow(keyConfig.isEnchanted())
                .build();
    }

    /**
     * Builds the crate item (what the player holds/places).
     * Tagged with PDC key {@code vc_crate_name} so the CrateListener can identify it.
     */
    public ItemStack buildCrateItem() {
        VaultCrates plugin = VaultCrates.getInstance();
        NamespacedKey crateKey = new NamespacedKey(plugin, "vc_crate_name");

        // Helper to tag a retrieved custom item with the crate PDC key
        java.util.function.Function<ItemStack, ItemStack> tag = item -> {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, name);
                item.setItemMeta(meta);
            }
            return item;
        };

        // ItemsAdder model
        if (iaBlock != null && !iaBlock.isBlank() && plugin.getItemsAdderHook().isEnabled()) {
            ItemStack item = plugin.getItemsAdderHook().getItem(iaBlock);
            if (item != null) return tag.apply(item);
        }

        // Oraxen model
        if (oraxenBlock != null && !oraxenBlock.isBlank() && plugin.getOraxenHook().isEnabled()) {
            ItemStack item = plugin.getOraxenHook().getItem(oraxenBlock);
            if (item != null) return tag.apply(item);
        }

        // Nexo model
        if (nexoBlock != null && !nexoBlock.isBlank() && plugin.getNexoHook().isEnabled()) {
            ItemStack item = plugin.getNexoHook().getItem(nexoBlock);
            if (item != null) return tag.apply(item);
        }

        return ItemBuilder.of(material)
                .name(ColorUtil.colorize(name))
                .lore(lores)
                .glow(enchanted)
                .customModelData(customModelData)
                .pdc(crateKey, PersistentDataType.STRING, name)
                .build();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public File getConfigFile() { return configFile; }
    public Material getMaterial() { return material; }
    public boolean isEnchanted() { return enchanted; }
    public boolean isOnlyPlaceable() { return onlyPlaceable; }
    public boolean isRewardsDontRepeat() { return rewardsDontRepeat; }
    public boolean isShiftInstantlyOpen() { return shiftInstantlyOpen; }
    public boolean isShiftInstantlyOpenAll() { return shiftInstantlyOpenAll; }
    public int getCustomModelData() { return customModelData; }
    public int getSize() { return size; }
    public int getPreviewSize() { return Math.max(9, Math.min(54, previewSize)); }
    public int getProximityDistance() { return proximityDistance; }
    public AnimationType getAnimationType() { return animationType; }
    public AnimationVisibility getVisibility() { return visibility; }
    public List<String> getHologramLines() { return Collections.unmodifiableList(hologramLines); }
    public List<String> getRewardHologramLines() { return Collections.unmodifiableList(rewardHologramLines); }
    public List<String> getLores() { return Collections.unmodifiableList(lores); }
    public List<String> getFinalMessage() { return Collections.unmodifiableList(finalMessage); }
    public List<String> getCommandsOnPlace() { return Collections.unmodifiableList(commandsOnPlace); }
    public List<String> getClosedCrateText() { return Collections.unmodifiableList(closedCrateText); }
    public boolean isPreviewEnabled() { return previewEnabled; }
    public boolean isShowChanceInPreview() { return showChanceInPreview; }
    public String getPreviewTitle() { return previewTitle; }
    public KeyConfig getKeyConfig() { return keyConfig; }
    public List<Reward> getPrizes() { return Collections.unmodifiableList(prizes); }
    public List<Reward> getBestPrizes() { return Collections.unmodifiableList(bestPrizes); }
    public List<String> getOnlyPlaceableLocations() { return Collections.unmodifiableList(onlyPlaceableLocations); }
    public String getMiddleAnimationItem() { return middleAnimationItem; }
    public int getMiddleAnimationItemCustomModelData() { return middleAnimationItemCustomModelData; }
    public String getIaBlock()     { return iaBlock; }
    public String getOraxenBlock() { return oraxenBlock; }
    public String getNexoBlock()   { return nexoBlock; }

    /**
     * Returns {@code true} if this crate uses an ItemsAdder furniture item
     * (i.e. the IA item's base material is not a placeable block, such as PAPER).
     *
     * <p>Furniture items cannot be placed via {@code BlockPlaceEvent}; VaultCrates
     * handles their placement by intercepting {@code PlayerInteractEvent} and
     * tagging the ArmorStand entity that ItemsAdder spawns.</p>
     */
    public boolean isFurnitureCrate() {
        if (iaBlock == null || iaBlock.isBlank()) return false;
        VaultCrates p = VaultCrates.getInstance();
        if (!p.getItemsAdderHook().isEnabled()) return false;
        ItemStack item = p.getItemsAdderHook().getItem(iaBlock);
        return item != null && !item.getType().isBlock();
    }
    public boolean isUseEconomy()         { return useEconomy; }
    public double getEconomyPrice()       { return economyPrice; }
    public boolean isUseWeight()          { return useWeight; }
    public boolean isSelectableRewards()  { return selectableRewards; }
    public int getSelectableRewardsCount(){ return selectableRewardsCount; }
    public List<Milestone> getMilestones(){ return Collections.unmodifiableList(milestones); }
    public boolean isPushbackEnabled()    { return pushbackEnabled; }
    public double getPushbackStrength()   { return pushbackStrength; }

    // -------------------------------------------------------------------------
    // Setters (for in-game editor)
    // -------------------------------------------------------------------------

    public void setAnimationType(AnimationType animationType) { this.animationType = animationType; }
    public void setVisibility(AnimationVisibility visibility) { this.visibility = visibility; }
    public void setSize(int size) { this.size = Math.max(1, size); }
    public void setMaterial(Material material) { this.material = material; }
    public void setEnchanted(boolean enchanted) { this.enchanted = enchanted; }
    public void setPreviewEnabled(boolean previewEnabled) { this.previewEnabled = previewEnabled; }
    public void setHologramLines(List<String> lines) { this.hologramLines = new ArrayList<>(lines); }
    public void setFinalMessage(List<String> msg) { this.finalMessage = new ArrayList<>(msg); }
    public void setShiftInstantlyOpen(boolean b) { this.shiftInstantlyOpen = b; }
}
