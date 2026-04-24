package de.itslarss.vaultcrates.pouch;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import de.itslarss.vaultcrates.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a configured pouch that gives random rewards when placed or right-clicked.
 */
public class Pouch {

    /** PDC key that marks an item as a VaultCrates pouch. */
    public static NamespacedKey POUCH_MARKER;

    /** PDC key storing the pouch name. */
    public static NamespacedKey POUCH_NAME_KEY;

    // -------------------------------------------------------------------------
    // Static key initialisation (called after VaultCrates is ready)
    // -------------------------------------------------------------------------

    public static void initKeys() {
        POUCH_MARKER = new NamespacedKey(VaultCrates.getInstance(), "vc_pouch");
        POUCH_NAME_KEY = new NamespacedKey(VaultCrates.getInstance(), "vc_pouch_name");
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String name;
    private final File configFile;

    private Material material;
    private Material standMaterial;
    private boolean enchanted;
    private boolean standEnchanted;
    private boolean onlyPlaceable;
    private int customModelData;

    private String displayName;
    private List<String> lores;
    private List<String> endHologram;
    private List<String> rewards;

    private int randomMin;
    private int randomMax;
    private boolean convertBigNumbers;

    private Pouch(String name, File configFile) {
        this.name = name;
        this.configFile = configFile;
        this.lores = new ArrayList<>();
        this.endHologram = new ArrayList<>();
        this.rewards = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Loads a pouch from a YAML config file.
     *
     * @param fileName the file name without extension (used as pouch ID)
     * @param file     the config file
     * @return the parsed pouch
     */
    public static Pouch fromConfig(String fileName, File file) {
        String name = fileName.replace(".yml", "");
        Pouch p = new Pouch(name, file);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String matStr = cfg.getString("Material", "CHEST");
        try { p.material = Material.valueOf(matStr.toUpperCase()); }
        catch (IllegalArgumentException e) { p.material = Material.CHEST; }

        String standStr = cfg.getString("PouchStand", matStr);
        try { p.standMaterial = Material.valueOf(standStr.toUpperCase()); }
        catch (IllegalArgumentException e) { p.standMaterial = p.material; }

        p.enchanted = cfg.getBoolean("Enchanted", false);
        p.standEnchanted = cfg.getBoolean("PouchStandEnchanted", false);
        p.onlyPlaceable = cfg.getBoolean("OnlyPlaceable", true);
        p.customModelData = cfg.getInt("CustomModelData", 0);
        p.displayName = cfg.getString("Name", name);
        p.lores = cfg.getStringList("Lores");
        p.endHologram = cfg.getStringList("EndHologram");
        p.rewards = cfg.getStringList("Rewards");
        p.randomMin = cfg.getInt("RandomNumberMin", 1);
        p.randomMax = cfg.getInt("RandomNumberMax", 10);
        p.convertBigNumbers = cfg.getBoolean("ConvertBigNumbers", false);

        return p;
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /** Builds the pouch item with proper PDC tags. */
    public ItemStack buildItem(int amount) {
        return ItemBuilder.of(material)
                .name(displayName)
                .lore(lores)
                .amount(amount)
                .glow(enchanted)
                .customModelData(customModelData)
                .pdc(POUCH_MARKER, PersistentDataType.BYTE, (byte) 1)
                .pdc(POUCH_NAME_KEY, PersistentDataType.STRING, name)
                .build();
    }

    public ItemStack buildItem() { return buildItem(1); }

    /** Returns a random number in the configured range. */
    public int resolveRandomNumber() {
        return NumberUtil.randomInt(randomMin, randomMax);
    }

    /**
     * Executes all configured reward commands for the player,
     * replacing {@code {player_name}} and {@code {random_number}} variables.
     *
     * @param player the player receiving rewards
     */
    public void executeRewards(Player player) {
        int random = resolveRandomNumber();
        String randomStr = convertBigNumbers ? NumberUtil.formatBig(random) : String.valueOf(random);

        for (String cmd : rewards) {
            if (cmd == null || cmd.isEmpty()) continue;
            String resolved = cmd
                    .replace("{player_name}", player.getName())
                    .replace("{random_number}", randomStr);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public File getConfigFile() { return configFile; }
    public Material getMaterial() { return material; }
    public Material getStandMaterial() { return standMaterial; }
    public boolean isEnchanted() { return enchanted; }
    public boolean isStandEnchanted() { return standEnchanted; }
    public boolean isOnlyPlaceable() { return onlyPlaceable; }
    public int getCustomModelData() { return customModelData; }
    public String getDisplayName() { return displayName; }
    public List<String> getLores() { return Collections.unmodifiableList(lores); }
    public List<String> getEndHologram() { return Collections.unmodifiableList(endHologram); }
    public List<String> getRewards() { return Collections.unmodifiableList(rewards); }
    public int getRandomMin() { return randomMin; }
    public int getRandomMax() { return randomMax; }
    public boolean isConvertBigNumbers() { return convertBigNumbers; }
}
