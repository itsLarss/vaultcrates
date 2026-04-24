package de.itslarss.vaultcrates.api.events;

import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called before a player opens a crate (before the animation starts).
 * Cancelling this event prevents the crate from being opened.
 */
public class CrateOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Crate crate;
    private final Location crateLocation;
    private final boolean usingVirtualKey;
    private boolean cancelled;

    public CrateOpenEvent(Player player, Crate crate, Location crateLocation, boolean usingVirtualKey) {
        this.player = player;
        this.crate = crate;
        this.crateLocation = crateLocation;
        this.usingVirtualKey = usingVirtualKey;
    }

    /** The player opening the crate. */
    public Player getPlayer() { return player; }

    /** The crate being opened. */
    public Crate getCrate() { return crate; }

    /** The block location of the crate. */
    public Location getCrateLocation() { return crateLocation; }

    /** Whether the player is using a virtual key (as opposed to a physical key item). */
    public boolean isUsingVirtualKey() { return usingVirtualKey; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
