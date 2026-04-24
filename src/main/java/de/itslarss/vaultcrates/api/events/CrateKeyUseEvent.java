package de.itslarss.vaultcrates.api.events;

import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player uses a key (physical or virtual) to open a crate.
 * Cancelling this event prevents the key from being consumed and the crate from opening.
 */
public class CrateKeyUseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Crate crate;
    private final boolean virtual;
    private boolean cancelled;

    public CrateKeyUseEvent(Player player, Crate crate, boolean virtual) {
        this.player = player;
        this.crate = crate;
        this.virtual = virtual;
    }

    /** The player using the key. */
    public Player getPlayer() { return player; }

    /** The crate being opened. */
    public Crate getCrate() { return crate; }

    /** Whether the key is virtual ({@code true}) or a physical item ({@code false}). */
    public boolean isVirtual() { return virtual; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
