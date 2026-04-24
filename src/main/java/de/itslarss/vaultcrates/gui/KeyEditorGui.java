package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.key.PhysicalKeyUtil;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * In-game key editor — lets admins configure the physical key for a crate without editing YAML.
 * Changes are in-memory; edit the crate YAML for permanent changes.
 */
public class KeyEditorGui extends ChestGui {

    private final Crate crate;
    private final Player player;

    /** Materials available in the material cycler. */
    private static final List<Material> KEY_MATERIALS = Arrays.asList(
            Material.TRIPWIRE_HOOK, Material.NETHER_STAR, Material.GOLD_NUGGET,
            Material.DIAMOND, Material.EMERALD, Material.BLAZE_ROD,
            Material.ENDER_PEARL, Material.TOTEM_OF_UNDYING, Material.HEART_OF_THE_SEA,
            Material.PHANTOM_MEMBRANE, Material.ECHO_SHARD, Material.AMETHYST_SHARD
    );

    public KeyEditorGui(VaultCrates plugin, Crate crate, Player player) {
        super(plugin);
        this.crate  = crate;
        this.player = player;
    }

    @Override public String getTitle() { return ColorUtil.colorize("&8Key-Editor: &6" + crate.getName()); }
    @Override public int getSize()    { return 45; }

    @Override
    public void populate() {
        fillBorder(getFiller());

        Crate.KeyConfig kc = crate.getKeyConfig();

        // Current key preview (center)
        setItem(22, PhysicalKeyUtil.createKey(crate));

        // ── Material cycler ──────────────────────────────────────────────────
        setItem(10, ItemBuilder.of(kc.getMaterial())
                .name("&6Key-Material")
                .addLore("&7Aktuell: &e" + kc.getMaterial().name(),
                         "",
                         "&aLinksklick: &7nächstes Material",
                         "&cRechtsklick: &7vorheriges Material").build(),
                e -> {
                    int idx = KEY_MATERIALS.indexOf(kc.getMaterial());
                    if (idx < 0) idx = 0;
                    if (e.isRightClick()) {
                        idx = (idx - 1 + KEY_MATERIALS.size()) % KEY_MATERIALS.size();
                    } else {
                        idx = (idx + 1) % KEY_MATERIALS.size();
                    }
                    kc.setMaterial(KEY_MATERIALS.get(idx));
                    open(player);
                });

        // ── Name editor ──────────────────────────────────────────────────────
        setItem(12, ItemBuilder.of(Material.NAME_TAG)
                .name("&6Key-Name bearbeiten")
                .addLore("&7Aktuell: " + ColorUtil.colorize(kc.getName()),
                         "",
                         "&aKlicken zum Bearbeiten &8(& für Farben)").build(),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ColorUtil.colorize("&7[&6VC&7] Neuen Key-Namen eingeben &8(& Farbcodes möglich):"));
                    new ChatInputListener(plugin, player, input -> {
                        kc.setName(input.trim());
                        plugin.getGuiManager().openKeyEditor(player, crate);
                    }, 30).register();
                });

        // ── Glow toggle ──────────────────────────────────────────────────────
        setItem(14, ItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("&6Glanz (Glow)")
                .addLore("&7Aktuell: " + (kc.isEnchanted() ? "&aAktiviert" : "&cDeaktiviert"),
                         "",
                         "&aKlicken zum Umschalten").build(),
                e -> { kc.setEnchanted(!kc.isEnchanted()); open(player); });

        // ── MatchNBT ─────────────────────────────────────────────────────────
        setItem(28, ItemBuilder.of(Material.COMPARATOR)
                .name("&6MatchNBT &7(empfohlen)")
                .addLore("&7Erkennt Keys über unsichtbare NBT-Tags.",
                         "&7Aktuell: " + (kc.isMatchNBT() ? "&aAktiviert" : "&cDeaktiviert"),
                         "",
                         "&aKlicken zum Umschalten").build(),
                e -> { kc.setMatchNBT(!kc.isMatchNBT()); open(player); });

        // ── MatchName ────────────────────────────────────────────────────────
        setItem(30, ItemBuilder.of(Material.PAPER)
                .name("&6MatchName")
                .addLore("&7Gleicht Keys über den Anzeigenamen ab.",
                         "&7Aktuell: " + (kc.isMatchName() ? "&aAktiviert" : "&cDeaktiviert"),
                         "",
                         "&aKlicken zum Umschalten").build(),
                e -> { kc.setMatchName(!kc.isMatchName()); open(player); });

        // ── ItemsAdder model info ─────────────────────────────────────────────
        String iaModel = kc.getIaKeyModel();
        setItem(32, ItemBuilder.of(Material.CHEST)
                .name("&6ItemsAdder-Modell")
                .addLore("&7Aktuell: " + (iaModel != null ? "&e" + iaModel : "&8Nicht gesetzt"),
                         "",
                         "&7Um ein IA-Modell zu setzen, füge in der",
                         "&7Crate-YAML unter &eKeyCrate:&7 hinzu:",
                         "&e  IAModel: namespace:key_id").build(), null);

        // ── Get key ───────────────────────────────────────────────────────────
        setItem(40, ItemBuilder.of(Material.LIME_DYE)
                .name("&aKey erhalten")
                .addLore("&7Gibt dir 1x den aktuellen Key.").build(),
                e -> {
                    ItemStack key = PhysicalKeyUtil.createKey(crate);
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(key);
                    overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    player.sendMessage(ColorUtil.colorize("&7[&6VC&7] &aKey erhalten!"));
                });

        // ── Back ──────────────────────────────────────────────────────────────
        setItem(36, ItemBuilder.of(Material.ARROW).name("&7\u00AB Zurück").build(),
                e -> plugin.getGuiManager().openCrateEditor(player, crate));

        // ── Save notice ───────────────────────────────────────────────────────
        setItem(44, ItemBuilder.of(Material.YELLOW_CONCRETE)
                .name("&eHinweis")
                .addLore("&7Änderungen gelten nur bis zum nächsten Reload.",
                         "&7Für permanente Änderungen die YAML bearbeiten.").build(), null);
    }
}
