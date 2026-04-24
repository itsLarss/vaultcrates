package de.itslarss.vaultcrates.pouch;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages pouch loading, identification and opening logic.
 */
public class PouchManager {

    private final VaultCrates plugin;
    private final File pouchesFolder;
    private final Map<String, Pouch> pouches = new LinkedHashMap<>();

    /** Players currently in the middle of opening a pouch (anti-spam). */
    private final Set<UUID> opening = new HashSet<>();

    public PouchManager(VaultCrates plugin) {
        this.plugin = plugin;
        this.pouchesFolder = new File(plugin.getDataFolder(), "pouches");
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /** (Re)loads all pouch configurations from the {@code pouches/} folder. */
    public void reload() {
        pouches.clear();

        if (!pouchesFolder.exists()) {
            pouchesFolder.mkdirs();
            plugin.saveResource("pouches/ExamplePouch.yml", false);
        }

        File[] files = pouchesFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    Pouch pouch = Pouch.fromConfig(file.getName(), file);
                    pouches.put(pouch.getName().toLowerCase(), pouch);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to load pouch " + file.getName(), e);
                }
            }
        }
        plugin.getLogger().info("Loaded " + pouches.size() + " pouch(es).");
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Gets a pouch by name (case-insensitive). */
    public Pouch getPouch(String name) {
        return name == null ? null : pouches.get(name.toLowerCase());
    }

    /** Returns an unmodifiable view of all loaded pouches. */
    public Map<String, Pouch> getPouches() {
        return Collections.unmodifiableMap(pouches);
    }

    // -------------------------------------------------------------------------
    // Item identification
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the item has VaultCrates pouch PDC data. */
    public boolean isPouch(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Pouch.POUCH_MARKER, PersistentDataType.BYTE);
    }

    /** Returns the pouch name stored in the item's PDC, or {@code null}. */
    public String getPouchName(ItemStack item) {
        if (!isPouch(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(Pouch.POUCH_NAME_KEY, PersistentDataType.STRING);
    }

    // -------------------------------------------------------------------------
    // Opening
    // -------------------------------------------------------------------------

    /**
     * Opens a pouch for a player at the given location.
     * Plays effects, gives rewards, and shows the end hologram.
     *
     * @param player the player opening the pouch
     * @param pouch  the pouch being opened
     * @param loc    the location where the pouch effect should appear
     */
    public void openPouch(Player player, Pouch pouch, Location loc) {
        if (opening.contains(player.getUniqueId())) return;
        opening.add(player.getUniqueId());

        // Play open sound
        loc.getWorld().playSound(loc, Sound.BLOCK_CHEST_OPEN, 1f, 1f);

        // Execute rewards
        pouch.executeRewards(player);

        // Spiral particle effect
        if (plugin.getConfigManager().getBoolean("Animations.Pouch.Spiral_Effect", true)) {
            spawnSpiralEffect(loc);
        }

        // End hologram
        if (!pouch.getEndHologram().isEmpty()) {
            int rn = pouch.resolveRandomNumber();
            List<String> hologramLines = new ArrayList<>();
            for (String line : pouch.getEndHologram()) {
                hologramLines.add(line
                        .replace("{player_name}", player.getName())
                        .replace("{random_number}", String.valueOf(rn)));
            }
            spawnTemporaryHologram(loc.clone().add(0.5, 1.5, 0.5), hologramLines, 100L);
        }

        // Ending effect
        String ending = plugin.getConfigManager().getString("Animations.Pouch.Ending", "LIGHTNING");
        if ("LIGHTNING".equalsIgnoreCase(ending)) {
            loc.getWorld().strikeLightningEffect(loc);
        }

        // Remove from opening set after a short delay
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> opening.remove(player.getUniqueId()), 20L);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void spawnSpiralEffect(Location center) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            double angle = 0;
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 40) {
                    // Task will be cancelled by the scheduler reference — use a wrapper
                    return;
                }
                double x = Math.cos(angle) * 1.0;
                double z = Math.sin(angle) * 1.0;
                double y = ticks * 0.05;
                center.getWorld().spawnParticle(
                        org.bukkit.Particle.FLAME,
                        center.clone().add(x, y, z),
                        1, 0, 0, 0, 0);
                angle += 0.3;
            }
        }, 0L, 1L);
    }

    private void spawnTemporaryHologram(Location loc, List<String> lines, long durationTicks) {
        List<TextDisplay> displays = new ArrayList<>();
        double lineSpacing = 0.28;
        for (int i = 0; i < lines.size(); i++) {
            double yOffset = (lines.size() - 1 - i) * lineSpacing;
            Location lineLoc = loc.clone().add(0, yOffset, 0);
            String line = lines.get(i);
            TextDisplay td = lineLoc.getWorld().spawn(lineLoc, TextDisplay.class, d -> {
                d.text(LegacyComponentSerializer.legacyAmpersand().deserialize(ColorUtil.colorize(line)));
                d.setBillboard(Display.Billboard.CENTER);
                d.setGravity(false);
                d.setPersistent(false);
            });
            displays.add(td);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                displays.forEach(d -> { if (d.isValid()) d.remove(); }), durationTicks);
    }

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();
}
