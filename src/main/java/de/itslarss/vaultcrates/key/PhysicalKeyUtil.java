package de.itslarss.vaultcrates.key;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility class for creating and identifying VaultCrates physical key items.
 * Uses {@link org.bukkit.persistence.PersistentDataContainer} to tag key items with crate names.
 */
public final class PhysicalKeyUtil {

    /** PDC key that marks an item as a VaultCrates key (value = 1). */
    public static NamespacedKey KEY_MARKER;

    /** PDC key storing the name of the crate this key opens. */
    public static NamespacedKey KEY_CRATE_NAME;

    /**
     * PDC key storing the per-item UUID used for anti-dupe protection.
     * Each key item receives a unique UUID at creation time. Once consumed,
     * the UUID is recorded in {@link UsedKeyStorage} and rejected on reuse.
     */
    public static NamespacedKey KEY_UUID;

    private PhysicalKeyUtil() {}

    /**
     * Must be called once in {@code VaultCrates.onEnable()} after {@code instance} is set.
     */
    public static void initKeys() {
        KEY_MARKER     = new NamespacedKey(VaultCrates.getInstance(), "vc_key");
        KEY_CRATE_NAME = new NamespacedKey(VaultCrates.getInstance(), "vc_crate_name");
        KEY_UUID       = new NamespacedKey(VaultCrates.getInstance(), "vc_key_uuid");
    }

    // -------------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------------

    /**
     * Creates a single physical key {@link ItemStack} for the given crate.
     * Each call produces exactly <em>one</em> key item stamped with a unique UUID
     * for anti-dupe protection. Call this method {@code amount} times to give
     * multiple keys rather than using stack sizes > 1.
     *
     * @param crate the crate this key opens
     * @return the tagged key item (amount = 1)
     */
    public static ItemStack createKey(Crate crate) {
        return createKey(crate, UUID.randomUUID());
    }

    /**
     * Creates a physical key with a specific UUID (used internally for restoring
     * persisted keys — never reuse an existing UUID for a new key).
     */
    private static ItemStack createKey(Crate crate, UUID keyUuid) {
        Crate.KeyConfig kc = crate.getKeyConfig();
        String uuidStr = keyUuid.toString();

        // Use ItemsAdder model if configured and plugin is available
        String iaModel = kc.getIaKeyModel();
        if (iaModel != null && !iaModel.isBlank()
                && VaultCrates.getInstance().getItemsAdderHook().isEnabled()) {
            ItemStack iaItem = VaultCrates.getInstance().getItemsAdderHook().getItem(iaModel);
            if (iaItem != null) {
                iaItem.setAmount(1);
                org.bukkit.inventory.meta.ItemMeta meta = iaItem.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(KEY_MARKER,     PersistentDataType.BYTE,   (byte) 1);
                    meta.getPersistentDataContainer().set(KEY_CRATE_NAME, PersistentDataType.STRING, crate.getName());
                    meta.getPersistentDataContainer().set(KEY_UUID,       PersistentDataType.STRING, uuidStr);
                    iaItem.setItemMeta(meta);
                }
                return iaItem;
            }
        }

        return ItemBuilder.of(kc.getMaterial())
                .name(kc.getName())
                .lore(kc.getLores())
                .amount(1)
                .glow(kc.isEnchanted())
                .pdc(KEY_MARKER,     PersistentDataType.BYTE,   (byte) 1)
                .pdc(KEY_CRATE_NAME, PersistentDataType.STRING, crate.getName())
                .pdc(KEY_UUID,       PersistentDataType.STRING, uuidStr)
                .build();
    }

    // -------------------------------------------------------------------------
    // Identification
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the item has VaultCrates key PDC data.
     */
    public static boolean isVaultCratesKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_MARKER, PersistentDataType.BYTE);
    }

    /**
     * Returns the crate name stored in the key's PDC, or {@code null} if not a key.
     */
    public static String getCrateName(ItemStack item) {
        if (!isVaultCratesKey(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(KEY_CRATE_NAME, PersistentDataType.STRING);
    }

    /**
     * Returns the anti-dupe UUID stored in the key's PDC, or {@code null} if the
     * key pre-dates the UUID system (legacy key without UUID tag).
     *
     * @param item the key item to inspect
     * @return the key UUID, or {@code null} for legacy keys
     */
    public static UUID getKeyUuid(ItemStack item) {
        if (!isVaultCratesKey(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String uuidStr = meta.getPersistentDataContainer().get(KEY_UUID, PersistentDataType.STRING);
        if (uuidStr == null) return null;
        try { return UUID.fromString(uuidStr); }
        catch (IllegalArgumentException e) { return null; }
    }

    // -------------------------------------------------------------------------
    // Matching
    // -------------------------------------------------------------------------

    /**
     * Determines whether an item matches the key configuration of a crate.
     * Respects the {@code MatchNBT}, {@code MatchName} and {@code MatchLore} flags.
     *
     * @param item  the item to check
     * @param crate the crate whose key config to match against
     * @return {@code true} if the item is a valid key for the crate
     */
    public static boolean matchesKey(ItemStack item, Crate crate) {
        if (item == null || item.getType() == Material.AIR) return false;
        Crate.KeyConfig kc = crate.getKeyConfig();

        // NBT check is the most reliable method
        if (kc.isMatchNBT()) {
            return isVaultCratesKey(item) && crate.getName().equalsIgnoreCase(getCrateName(item));
        }

        // Material check (always required)
        if (item.getType() != kc.getMaterial()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Name check
        if (kc.isMatchName()) {
            String expected = ColorUtil.colorize(kc.getName());
            String actual = meta.hasDisplayName()
                    ? LegacyComponentSerializer.legacySection().serialize(meta.displayName())
                    : "";
            if (!expected.equals(actual)) return false;
        }

        // Lore check
        if (kc.isMatchLore() && !kc.getLores().isEmpty()) {
            if (!meta.hasLore()) return false;
            List<String> expectedLore = kc.getLores().stream()
                    .map(ColorUtil::colorize)
                    .collect(Collectors.toList());
            List<String> actualLore = meta.lore() == null ? List.of()
                    : meta.lore().stream()
                    .map(c -> LegacyComponentSerializer.legacySection().serialize(c))
                    .collect(Collectors.toList());
            if (!actualLore.containsAll(expectedLore)) return false;
        }

        return true;
    }
}
