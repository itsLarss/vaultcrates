package de.itslarss.vaultcrates.animation;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.impl.*;
import de.itslarss.vaultcrates.api.events.CrateOpenEvent;
import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages active animation sessions and dispatches crate openings to the correct animation.
 */
public class AnimationManager {

    private final VaultCrates plugin;

    /** Active animation sessions keyed by player UUID. */
    private final Map<UUID, AnimationSession> activeSessions = new HashMap<>();

    /** Registered animation implementations. */
    private final Map<AnimationType, CrateAnimation> animations = new EnumMap<>(AnimationType.class);

    public AnimationManager(VaultCrates plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        register(new RoundAnimation(plugin));
        register(new CosmicAnimation(plugin));
        register(new DisplayAnimation(plugin));
        register(new PyramidAnimation(plugin));
        register(new ContrabandAnimation(plugin));
        register(new InstantAnimation(plugin));
        register(new AirStrikeAnimation(plugin));
        register(new BreakoutAnimation(plugin));
        register(new MeteorShowerAnimation(plugin));
        register(new YinYangAnimation(plugin));
        register(new QuickAnimation(plugin));
        register(new Round2Animation(plugin));
    }

    private void register(CrateAnimation anim) {
        animations.put(anim.getType(), anim);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a crate animation for the given player.
     * Fires {@link CrateOpenEvent}; cancelling it prevents the animation from starting.
     *
     * @param player    the player opening the crate
     * @param crate     the crate being opened
     * @param crateLoc  the block location of the crate
     */
    public void startAnimation(Player player, Crate crate, Location crateLoc) {
        if (crate == null || isAnimating(player)) return;

        // Remove hologram temporarily
        plugin.getHologramManager().removeHologram(
                plugin.getHologramManager().locationToId(crateLoc));

        // Build session and select rewards before firing the event
        AnimationSession session = new AnimationSession(player, crate, crateLoc);
        session.setSelectedRewards(crate.pickPrizes(player, crate.getSize()));
        if (!crate.getBestPrizes().isEmpty()) {
            session.setBestReward(crate.pickBestPrize(player));
        }

        // Fire cancellable event
        CrateOpenEvent event = new CrateOpenEvent(player, crate, crateLoc, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // Restore hologram
            if (!crate.getHologramLines().isEmpty()) {
                plugin.getHologramManager().spawnHologram(
                        plugin.getHologramManager().locationToId(crateLoc),
                        crateLoc, crate.getHologramLines());
            }
            return;
        }

        activeSessions.put(player.getUniqueId(), session);

        CrateAnimation anim = animations.getOrDefault(crate.getAnimationType(), animations.get(AnimationType.ROUND));
        anim.start(session);
    }

    /**
     * Force-stops any active animation for the given player.
     */
    public void forceStop(Player player) {
        AnimationSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            CrateAnimation anim = animations.getOrDefault(
                    session.getCrate().getAnimationType(), animations.get(AnimationType.ROUND));
            anim.forceStop(session);
        }
    }

    /**
     * Called by animations when they finish to remove the session from tracking.
     */
    public void onAnimationFinished(AnimationSession session) {
        activeSessions.remove(session.getPlayerUUID());
    }

    /** Returns {@code true} if the player is currently in an animation. */
    public boolean isAnimating(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /** Returns the number of currently running animations. */
    public int getActiveCount() { return activeSessions.size(); }

    /** Returns an unmodifiable view of all active sessions. */
    public Map<UUID, AnimationSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    /** Returns the animation implementation for the given type. */
    public CrateAnimation getAnimation(AnimationType type) {
        return animations.get(type);
    }

    /** Stops all active animations (called on plugin disable). */
    public void stopAll() {
        new ArrayList<>(activeSessions.values()).forEach(s -> {
            s.cleanup();
            // Restore holograms
            Crate crate = s.getCrate();
            if (!crate.getHologramLines().isEmpty()) {
                plugin.getHologramManager().spawnHologram(
                        plugin.getHologramManager().locationToId(s.getCrateLocation()),
                        s.getCrateLocation(), crate.getHologramLines());
            }
        });
        activeSessions.clear();
    }
}
