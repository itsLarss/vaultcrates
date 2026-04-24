package de.itslarss.vaultcrates.hologram;

import de.itslarss.vaultcrates.util.ColorUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a multi-line hologram above a crate location.
 * Each text line is a separate {@link TextDisplay} entity (Paper 1.19.4+).
 */
public class CrateHologram {

    private static final double LINE_SPACING = 0.28;
    private static final double BASE_HEIGHT  = 1.6;

    private final String id;
    private Location baseLocation;
    private List<String> lines;
    private final List<TextDisplay> displays = new ArrayList<>();

    public CrateHologram(String id, Location baseLocation, List<String> lines) {
        this.id = id;
        this.baseLocation = baseLocation;
        this.lines = new ArrayList<>(lines);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Spawns TextDisplay entities for each line at the base location.
     * Lines are ordered top-to-bottom (first line is highest).
     */
    public void spawn() {
        if (baseLocation == null || baseLocation.getWorld() == null) return;
        remove(); // Clean up any existing displays first

        for (int i = 0; i < lines.size(); i++) {
            double yOffset = BASE_HEIGHT + (lines.size() - 1 - i) * LINE_SPACING;
            Location lineLoc = baseLocation.clone().add(0.5, yOffset, 0.5);
            final String text = lines.get(i);

            TextDisplay td = lineLoc.getWorld().spawn(lineLoc, TextDisplay.class, d -> {
                d.text(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(ColorUtil.colorize(text)));
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setGravity(false);
                d.setPersistent(false);
                d.setShadowed(true);
                d.setSeeThrough(false);
            });
            displays.add(td);
        }
    }

    /** Removes all TextDisplay entities belonging to this hologram. */
    public void remove() {
        displays.removeIf(d -> {
            if (d != null && d.isValid()) d.remove();
            return true;
        });
    }

    /**
     * Replaces the lines and re-spawns all displays.
     *
     * @param newLines the new text lines
     */
    public void update(List<String> newLines) {
        remove();
        this.lines = new ArrayList<>(newLines);
        spawn();
    }

    /**
     * Returns {@code true} if at least one display entity is currently alive.
     */
    public boolean isSpawned() {
        return !displays.isEmpty() && displays.get(0).isValid();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public Location getBaseLocation() { return baseLocation; }
    public List<String> getLines() { return Collections.unmodifiableList(lines); }
}
