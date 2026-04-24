package de.itslarss.vaultcrates.api.events;

import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Called after a crate has been fully opened and all rewards have been given.
 * This event is not cancellable.
 */
public class CrateOpenedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Crate crate;
    private final List<Reward> rewards;
    private final Reward bestReward;

    public CrateOpenedEvent(Player player, Crate crate, List<Reward> rewards, @Nullable Reward bestReward) {
        this.player = player;
        this.crate = crate;
        this.rewards = Collections.unmodifiableList(rewards);
        this.bestReward = bestReward;
    }

    /** The player who opened the crate. */
    public Player getPlayer() { return player; }

    /** The crate that was opened. */
    public Crate getCrate() { return crate; }

    /** The normal prizes the player received. */
    public List<Reward> getRewards() { return rewards; }

    /** The best prize the player received, or {@code null} if none was configured. */
    @Nullable public Reward getBestReward() { return bestReward; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
