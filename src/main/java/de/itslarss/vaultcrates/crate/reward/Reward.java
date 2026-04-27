package de.itslarss.vaultcrates.crate.reward;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.hook.ItemsAdderHook;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import de.itslarss.vaultcrates.util.NumberUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single reward that can be won from a crate.
 * Supports items, commands, or both, with configurable chances and permissions.
 */
public class Reward {

    /** A single enchantment entry stored with the reward. */
    public record EnchantmentEntry(Enchantment enchantment, int level) {}

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String id;
    private String name;
    private String previewName;
    private Material material;
    private boolean glow;
    private boolean giveItem;
    private int customModelData;
    private int amount;
    private int randomNumberMin;
    private int randomNumberMax;
    private boolean randomNumberDecimals;
    private String base64Texture;
    private String iaItemId;     // ItemsAdder namespaced id, e.g. "itemsadder:ruby_sword"
    private String oraxenItemId; // Oraxen item id, e.g. "my_custom_sword"
    private String nexoItemId;   // Nexo item id
    private String eiItemId;     // ExecutableItems item id
    private String mmoItemId;    // MMOItems "TYPE:id" pair, e.g. "SWORD:fireblade"
    private String requiredPermission;
    private List<String> lores;
    private List<String> previewLores;
    private List<String> commands;
    private List<String> messagesToPlayer;
    private List<String> broadcastMessages;
    private List<String> blacklistedPerms;
    private List<EnchantmentEntry> enchantments;
    private double chance;
    private double weight;       // alternative to chance — used when crate has UseWeight: true (0 = not set)
    private int globalLimit;     // 0 = unlimited — max total times this reward can drop
    private int playerLimit;     // 0 = unlimited — max times per player
    private List<BundledItem> bundledItems; // additional items given alongside the main item
    private String rarityId;     // e.g. "legendar", "episch" — maps to Rarity registry
    private String rewardType;   // optional label for the footer, e.g. "Rang/Permission", "Geld"
    private List<String> enchantCommands; // commands to apply custom-enchantment-plugin enchants

    // -------------------------------------------------------------------------
    // Constructor (private — use builder or fromConfig)
    // -------------------------------------------------------------------------

    private Reward(String id) {
        this.id = id;
        this.lores = new ArrayList<>();
        this.previewLores = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.messagesToPlayer = new ArrayList<>();
        this.broadcastMessages = new ArrayList<>();
        this.blacklistedPerms = new ArrayList<>();
        this.enchantments = new ArrayList<>();
        this.enchantCommands = new ArrayList<>();
        this.amount = 1;
        this.material = Material.STONE;
        this.rarityId = "common";
        this.bundledItems = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Factory — parse from YAML ConfigurationSection
    // -------------------------------------------------------------------------

    /**
     * Parses a {@link Reward} from a YAML {@link ConfigurationSection}.
     *
     * @param id      the key of this section (used as reward ID)
     * @param section the configuration section to read from
     * @return the parsed reward
     */
    public static Reward fromConfig(String id, ConfigurationSection section) {
        Reward r = new Reward(id);

        // Custom item model fields (checked before Material)
        r.oraxenItemId = section.getString("OraxenModel", null);
        r.nexoItemId   = section.getString("NexoModel",   null);
        r.eiItemId     = section.getString("EIModel",     null);
        r.mmoItemId    = section.getString("MMOItem",     null);

        // Material — supports standard Bukkit materials and ItemsAdder "namespace:id" format
        String matStr = section.getString("Material", "STONE");
        ItemsAdderHook iaHook = VaultCrates.getInstance().getItemsAdderHook();
        if (matStr.contains(":") && iaHook != null && iaHook.isEnabled()) {
            // Looks like an ItemsAdder namespaced id
            r.iaItemId = matStr;
            r.material = Material.PAPER; // invisible fallback for display
        } else {
            try {
                r.material = Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                r.material = Material.STONE;
            }
        }

        r.name = section.getString("Name", id);
        r.previewName = section.getString("PreviewName", r.name);
        r.glow = section.getBoolean("Glow", false);
        r.giveItem = section.getBoolean("GiveItem", false);
        r.customModelData = section.getInt("CustomModelData", 0);
        r.amount = Math.max(1, section.getInt("Amount", 1));
        r.randomNumberMin = section.getInt("RandomNumberMin", 0);
        r.randomNumberMax = section.getInt("RandomNumberMax", 0);
        r.randomNumberDecimals = section.getBoolean("RandomNumberDecimals", false);
        r.base64Texture = section.getString("Base64Texture", null);
        r.requiredPermission = section.getString("Permission", null);
        r.chance = section.getDouble("Chance", 10.0);
        r.weight = section.getDouble("Weight", 0.0);
        r.globalLimit = section.getInt("GlobalLimit", 0);
        r.playerLimit = section.getInt("PlayerLimit", 0);
        r.rarityId       = section.getString("Rarity", "common");
        r.rewardType     = section.getString("Type",   null); // null = use configured fallback
        r.enchantCommands = section.getStringList("EnchantCommands");

        // Bundled extra items (BundledItems: section)
        ConfigurationSection bundledSection = section.getConfigurationSection("BundledItems");
        if (bundledSection != null) {
            for (String bKey : bundledSection.getKeys(false)) {
                ConfigurationSection bSec = bundledSection.getConfigurationSection(bKey);
                if (bSec != null) r.bundledItems.add(BundledItem.fromConfig(bSec));
            }
        }

        r.lores = section.getStringList("Lores");
        r.previewLores = section.getStringList("PreviewLores");
        r.commands = section.getStringList("Commands");
        r.messagesToPlayer = section.getStringList("MessagesToPlayer");
        r.broadcastMessages = section.getStringList("BroadcastMessages");
        r.blacklistedPerms = section.getStringList("BlacklistedPerms");

        // Enchantments — supports both formats:
        //   Map:  "SHARPNESS: 5"  (preferred, used in example configs)
        //   List: "- SHARPNESS:5" (legacy fallback)
        ConfigurationSection enchSection = section.getConfigurationSection("Enchantments");
        if (enchSection != null) {
            for (String key : enchSection.getKeys(false)) {
                int level = enchSection.getInt(key, 1);
                Enchantment enchantment = Registry.ENCHANTMENT.get(
                        NamespacedKey.minecraft(key.toLowerCase()));
                if (enchantment != null) {
                    r.enchantments.add(new EnchantmentEntry(enchantment, level));
                }
            }
        } else {
            for (String ench : section.getStringList("Enchantments")) {
                if (ench == null || ench.isEmpty()) continue;
                String[] parts = ench.split(":");
                try {
                    Enchantment enchantment = Registry.ENCHANTMENT.get(
                            NamespacedKey.minecraft(parts[0].toLowerCase()));
                    int level = parts.length > 1 ? NumberUtil.parseIntSafe(parts[1], 1) : 1;
                    if (enchantment != null) {
                        r.enchantments.add(new EnchantmentEntry(enchantment, level));
                    }
                } catch (Exception ignored) {}
            }
        }

        return r;
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /**
     * Checks whether a player is eligible to receive this reward.
     * Verifies required permission and ensures none of the blacklisted permissions are held.
     */
    public boolean canReceive(Player player) {
        if (requiredPermission != null && !requiredPermission.isEmpty()) {
            if (!player.hasPermission(requiredPermission)) return false;
        }
        for (String perm : blacklistedPerms) {
            if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) return false;
        }
        return true;
    }

    /**
     * Builds the display {@link ItemStack} for this reward using the configured material,
     * name, lore, glow and skull texture.
     */
    public ItemStack buildDisplayItem() {
        VaultCrates plugin = VaultCrates.getInstance();

        // Oraxen custom item
        if (oraxenItemId != null && plugin.getOraxenHook().isEnabled()) {
            ItemStack item = plugin.getOraxenHook().getItem(oraxenItemId);
            if (item != null) { return applyRewardMeta(item.clone()); }
        }

        // Nexo custom item
        if (nexoItemId != null && plugin.getNexoHook().isEnabled()) {
            ItemStack item = plugin.getNexoHook().getItem(nexoItemId);
            if (item != null) { return applyRewardMeta(item.clone()); }
        }

        // ExecutableItems custom item — getItem(id, player): pass null for no player context
        if (eiItemId != null && plugin.getExecutableItemsHook().isEnabled()) {
            ItemStack item = plugin.getExecutableItemsHook().getItem(eiItemId, null);
            if (item != null) { item = item.clone(); item.setAmount(Math.max(1, amount)); return item; }
        }

        // MMOItems custom item — config format "TYPE:itemId" e.g. "SWORD:fire_blade"
        if (mmoItemId != null && plugin.getMmoItemsHook().isEnabled()) {
            String[] mmoparts = mmoItemId.split(":", 2);
            ItemStack item = mmoparts.length == 2
                    ? plugin.getMmoItemsHook().getItem(mmoparts[0], mmoparts[1])
                    : null;
            if (item != null) { item = item.clone(); item.setAmount(Math.max(1, amount)); return item; }
        }

        // ItemsAdder custom item — apply reward Name / Lore / Glow on top of the IA base item
        if (iaItemId != null && plugin.getItemsAdderHook().isEnabled()) {
            ItemStack iaItem = plugin.getItemsAdderHook().getItem(iaItemId);
            if (iaItem != null) { return applyRewardMeta(iaItem.clone()); }
        }

        // Standard Bukkit item
        ItemBuilder builder = ItemBuilder.of(material)
                .name(name)
                .lore(lores)
                .amount(amount)
                .glow(glow)
                .customModelData(customModelData);

        if (base64Texture != null && material == Material.PLAYER_HEAD) {
            builder.skullTexture(base64Texture);
        }
        for (EnchantmentEntry entry : enchantments) {
            builder.enchantment(entry.enchantment(), entry.level());
        }
        ItemStack result = builder.build();
        appendFooter(result);
        return result;
    }

    /**
     * Appends the reward footer lines (Gewinntyp / Seltenheit) to the item's lore
     * if {@code RewardFooter.Enabled} is set to {@code true} in {@code config.yml}.
     *
     * <p>Supported placeholders in {@code RewardFooter.Lines}:
     * <ul>
     *   <li>{@code {reward_type}} — the reward's {@code Type:} value, or the configured fallback</li>
     *   <li>{@code {rarity_color}} — the leading color/format codes of the rarity display name</li>
     *   <li>{@code {rarity_name}} — the rarity display name without leading color codes</li>
     * </ul>
     */
    /**
     * Applies this reward's {@code Name}, {@code Lores}, {@code Amount} and {@code Glow}
     * to a custom-plugin item (ItemsAdder, Oraxen, Nexo) that already has its own
     * model/texture data set.  The item is modified in place and returned.
     *
     * <p>MMOItems and ExecutableItems are intentionally excluded — those items carry
     * intrinsic stat lore that must not be overwritten.</p>
     */
    private ItemStack applyRewardMeta(ItemStack item) {
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) { appendFooter(item); return item; }

        // Display name — supports &codes, &#RRGGBB hex, MiniMessage, :ia_icon:
        if (name != null && !name.isEmpty()) {
            meta.displayName(de.itslarss.vaultcrates.util.ColorUtil.toComponent(name));
        }

        // Lore — same rich-text support per line
        if (lores != null && !lores.isEmpty()) {
            meta.lore(de.itslarss.vaultcrates.util.ColorUtil.toComponents(lores));
        }

        // Glow
        if (glow) {
            org.bukkit.enchantments.Enchantment infinity =
                    org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("infinity"));
            if (infinity != null) {
                meta.addEnchant(infinity, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        }

        item.setItemMeta(meta);
        appendFooter(item);
        return item;
    }

    private void appendFooter(ItemStack item) {
        VaultCrates plugin = VaultCrates.getInstance();
        if (!plugin.getConfigManager().getBoolean("RewardFooter.Enabled", false)) return;

        List<String> lines = plugin.getConfigManager().getStringList("RewardFooter.Lines");
        if (lines.isEmpty()) return;

        Rarity rarity  = getRarity();
        String fallback = plugin.getConfigManager().getString("RewardFooter.Fallback", "Reward");
        String type     = (rewardType != null && !rewardType.isBlank()) ? rewardType : fallback;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : lines) {
            String resolved = line
                    .replace("{reward_type}", type)
                    .replace("{rarity_name}", rarity.getDisplayName());
            lore.add(ColorUtil.toComponent(resolved));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Returns a random integer between {@link #randomNumberMin} and {@link #randomNumberMax}.
     * Returns 0 if no random number range is configured.
     */
    public int resolveRandomNumber() {
        if (randomNumberMin >= randomNumberMax && randomNumberMax == 0) return 0;
        return NumberUtil.randomInt(randomNumberMin, randomNumberMax);
    }

    /**
     * Returns a random double in the configured range (for decimal random numbers).
     */
    public double resolveRandomDouble() {
        if (randomNumberMin >= randomNumberMax && randomNumberMax == 0) return 0;
        return NumberUtil.randomDouble(randomNumberMin, randomNumberMax);
    }

    /**
     * Replaces common variables in a string for this reward and player.
     *
     * @param text   the template string
     * @param player the player opening the crate
     * @return the string with variables replaced
     */
    public String replaceVariables(String text, Player player) {
        if (text == null) return "";
        String rn = randomNumberDecimals
                ? String.valueOf(NumberUtil.round(resolveRandomDouble(), 2))
                : String.valueOf(resolveRandomNumber());
        return text
                .replace("{player_name}", player.getName())
                .replace("{player_displayname}", player.getDisplayName())
                .replace("{random_number}", rn)
                .replace("{new_random_number}", randomNumberDecimals
                        ? String.valueOf(NumberUtil.round(resolveRandomDouble(), 2))
                        : String.valueOf(resolveRandomNumber()));
    }

    /** Applies {@link #replaceVariables(String, Player)} to each line. */
    public List<String> replaceVariables(List<String> lines, Player player) {
        if (lines == null) return Collections.emptyList();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(replaceVariables(line, player));
        return result;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPreviewName() { return previewName; }
    public Material getMaterial() { return material; }
    public boolean isGlow() { return glow; }
    public boolean isGiveItem() { return giveItem; }
    public int getCustomModelData() { return customModelData; }
    public int getAmount() { return amount; }
    public int getRandomNumberMin() { return randomNumberMin; }
    public int getRandomNumberMax() { return randomNumberMax; }
    public boolean isRandomNumberDecimals() { return randomNumberDecimals; }
    public String getBase64Texture() { return base64Texture; }
    public String getRequiredPermission() { return requiredPermission; }
    public List<String> getLores() { return Collections.unmodifiableList(lores); }
    public List<String> getPreviewLores() { return Collections.unmodifiableList(previewLores); }
    public List<String> getCommands() { return Collections.unmodifiableList(commands); }
    public List<String> getMessagesToPlayer() { return Collections.unmodifiableList(messagesToPlayer); }
    public List<String> getBroadcastMessages() { return Collections.unmodifiableList(broadcastMessages); }
    public List<String> getBlacklistedPerms() { return Collections.unmodifiableList(blacklistedPerms); }
    public List<EnchantmentEntry> getEnchantments() { return Collections.unmodifiableList(enchantments); }
    public double getChance() { return chance; }
    public double getWeight() { return weight; }
    public int getGlobalLimit() { return globalLimit; }
    public int getPlayerLimit() { return playerLimit; }
    public List<BundledItem> getBundledItems() { return Collections.unmodifiableList(bundledItems); }
    public boolean hasBundledItems() { return !bundledItems.isEmpty(); }
    public boolean hasRandomNumber() { return randomNumberMax > 0 && randomNumberMax > randomNumberMin; }
    public String getRarityId() { return rarityId; }
    /** Returns the resolved {@link Rarity} for this reward. Never null. */
    public Rarity getRarity() { return Rarity.get(rarityId); }
    /** Returns the manually configured reward type label, or {@code null} if not set. */
    public String getRewardType() { return rewardType; }
    /** Returns commands to apply custom-enchantment-plugin enchants after the item is given. */
    public List<String> getEnchantCommands() { return Collections.unmodifiableList(enchantCommands); }

    // -------------------------------------------------------------------------
    // Setters (for in-game editor)
    // -------------------------------------------------------------------------

    public void setName(String name) { this.name = name; }
    public void setPreviewName(String previewName) { this.previewName = previewName; }
    public void setMaterial(Material material) { this.material = material; }
    public void setGlow(boolean glow) { this.glow = glow; }
    public void setGiveItem(boolean giveItem) { this.giveItem = giveItem; }
    public void setChance(double chance) { this.chance = chance; }
    public void setCommands(List<String> commands) { this.commands = commands; }
    public void setLores(List<String> lores) { this.lores = lores; }
    public void setRequiredPermission(String perm) { this.requiredPermission = perm; }
    public void setAmount(int amount) { this.amount = Math.max(1, amount); }
    public void setRarityId(String rarityId) { this.rarityId = rarityId; }
}
