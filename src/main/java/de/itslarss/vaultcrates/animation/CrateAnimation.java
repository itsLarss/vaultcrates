package de.itslarss.vaultcrates.animation;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.api.events.CrateOpenedEvent;
import de.itslarss.vaultcrates.crate.Milestone;
import de.itslarss.vaultcrates.crate.reward.Rarity;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.crate.reward.BundledItem;
import de.itslarss.vaultcrates.crate.reward.RewardLogger;
import de.itslarss.vaultcrates.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for all VaultCrates animations.
 * Provides helper utilities for entity spawning, particles, sounds and reward delivery.
 */
public abstract class CrateAnimation {

    protected final VaultCrates plugin;

    protected CrateAnimation(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Abstract interface
    // -------------------------------------------------------------------------

    /** Returns the animation type this class implements. */
    public abstract AnimationType getType();

    /**
     * Starts the animation for the given session.
     * Implementations MUST call {@link #finishAnimation(AnimationSession)} exactly once when done.
     */
    public abstract void start(AnimationSession session);

    /**
     * Force-stops the animation (e.g. player disconnect).
     * Default implementation: cleanup session.
     */
    public void forceStop(AnimationSession session) {
        session.cleanup();
        plugin.getHologramManager().spawnHologram(
                plugin.getHologramManager().locationToId(session.getCrateLocation()),
                session.getCrateLocation(),
                session.getCrate().getHologramLines());
    }

    // -------------------------------------------------------------------------
    // Protected helpers
    // -------------------------------------------------------------------------

    /**
     * Spawns an {@link ItemDisplay} entity and registers it in the session.
     *
     * @param loc       the location to spawn at
     * @param item      the item stack to display
     * @param billboard the billboard mode
     * @return the spawned entity
     */
    protected ItemDisplay spawnItemDisplay(AnimationSession session, Location loc,
                                           ItemStack item, Display.Billboard billboard) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setBillboard(billboard);
            d.setGravity(false);
            d.setPersistent(false);
        });
        session.addEntity(display);
        return display;
    }

    /**
     * Spawns a {@link TextDisplay} entity and registers it in the session.
     */
    protected TextDisplay spawnTextDisplay(AnimationSession session, Location loc, String text) {
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(ColorUtil.colorize(text)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setGravity(false);
            d.setPersistent(false);
        });
        session.addEntity(td);
        return td;
    }

    /** Spawns particles at the given location. Silently ignores particles that require extra data. */
    protected void spawnParticles(Location loc, Particle particle, int count, double spread) {
        if (loc.getWorld() == null) return;
        try {
            loc.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, 0);
        } catch (Exception ignored) {
            // Some particles (e.g. DUST, END_ROD) require a data class on certain server versions
        }
    }

    /**
     * Applies a rarity-based glow outline to an ItemDisplay entity.
     * Does nothing if the rarity has no configured glow colour.
     */
    protected void applyRarityGlow(ItemDisplay display, Reward reward) {
        if (reward == null || !display.isValid()) return;
        Color color = reward.getRarity().getGlowColor();
        if (color == null) return;
        display.setGlowing(true);
        display.setGlowColorOverride(color);
    }

    /**
     * Spawns a billboard TextDisplay above {@code loc} showing the reward name and rarity.
     * The entity is registered in the session and cleaned up automatically.
     */
    protected TextDisplay spawnWinnerLabel(AnimationSession session, Location loc, Reward reward) {
        if (reward == null || loc.getWorld() == null) return null;
        Rarity rarity = reward.getRarity();
        Component nameComp   = ColorUtil.toComponent(reward.getName());
        Component rarityComp = ColorUtil.toComponent(rarity.getRawDisplayName());
        Component full = nameComp.append(Component.newline()).append(rarityComp);

        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(full);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setGravity(false);
            d.setPersistent(false);
            d.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            d.setShadowed(true);
        });
        session.addEntity(td);
        return td;
    }

    /** Plays a sound at the given location. */
    protected void playSound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    /** Strikes a silent lightning bolt at the location. */
    protected void strikeLightningEffect(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().strikeLightningEffect(loc);
    }

    /**
     * Schedules a repeating task and registers it in the session.
     *
     * @param session the current session
     * @param run     the task runnable
     * @param delay   initial delay in ticks
     * @param period  repeat period in ticks
     * @return the created task
     */
    protected BukkitTask runRepeating(AnimationSession session, Runnable run, long delay, long period) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, run, delay, period);
        session.addTask(task);
        return task;
    }

    /**
     * Schedules a one-time delayed task and registers it in the session.
     */
    protected BukkitTask runLater(AnimationSession session, Runnable run, long delay) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, run, delay);
        session.addTask(task);
        return task;
    }

    // -------------------------------------------------------------------------
    // Reward delivery
    // -------------------------------------------------------------------------

    /**
     * Gives all selected rewards (and best reward) to the player.
     * Fires the {@link CrateOpenedEvent}.
     * Also: logs rewards, updates pity counters, increments limit counters, sends rarity broadcast.
     */
    protected void giveRewards(AnimationSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) return;

        String crateName = session.getCrate().getName();
        RewardLogger logger = new RewardLogger(plugin);

        // Normal prizes
        for (Reward reward : session.getSelectedRewards()) {
            deliverReward(player, reward);
            logger.log(player, crateName, reward);
            updatePityAndLimits(player, crateName, reward);
            sendRarityBroadcast(player, reward);
        }

        // Best prize
        if (session.getBestReward() != null) {
            deliverReward(player, session.getBestReward());
            logger.log(player, crateName, session.getBestReward());
        }

        // Milestones
        checkMilestones(session, player);

        // Fire API event
        CrateOpenedEvent event = new CrateOpenedEvent(
                player, session.getCrate(),
                session.getSelectedRewards(),
                session.getBestReward());
        Bukkit.getPluginManager().callEvent(event);
    }

    private void updatePityAndLimits(Player player, String crate, Reward reward) {
        var storage = plugin.getStorageManager();

        // Increment reward limit counters
        storage.getRewardLimitStorage().incrementGlobal(crate, reward.getId());
        storage.getRewardLimitStorage().incrementPlayer(player.getUniqueId(), crate, reward.getId());

        // Reset pity counter for the dropped rarity; increment all others
        String droppedRarityId = reward.getRarityId();
        for (Rarity r : de.itslarss.vaultcrates.crate.reward.Rarity.getAll().values()) {
            if (r.getPityAfter() > 0) {
                if (r.getId().equals(droppedRarityId)) {
                    storage.getPityStorage().reset(player.getUniqueId(), crate, r.getId());
                } else {
                    storage.getPityStorage().increment(player.getUniqueId(), crate, r.getId());
                }
            }
        }
    }

    private void sendRarityBroadcast(Player player, Reward reward) {
        Rarity rarity = reward.getRarity();
        if (!rarity.isBroadcast()) return;
        String msg = rarity.getBroadcastMessage()
                .replace("{player}", player.getName())
                .replace("{item}",   ColorUtil.colorize(reward.getName()))
                .replace("{rarity}", rarity.getDisplayName());
        String colored = ColorUtil.colorize(msg);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(colored));
    }

    private void checkMilestones(AnimationSession session, Player player) {
        var milestoneStorage = plugin.getStorageManager().getMilestoneStorage();
        String crate = session.getCrate().getName();
        int opens = milestoneStorage.incrementAndGet(player.getUniqueId(), crate);

        for (Milestone ms : session.getCrate().getMilestones()) {
            boolean shouldFire;
            if (ms.isResetAfter()) {
                shouldFire = opens % ms.getOpenCount() == 0;
            } else {
                shouldFire = opens == ms.getOpenCount()
                        && !milestoneStorage.isClaimed(player.getUniqueId(), crate, ms.getId());
            }
            if (shouldFire) {
                milestoneStorage.setClaimed(player.getUniqueId(), crate, ms.getId());
                if (ms.hasReward()) deliverReward(player, ms.getReward());
                player.sendMessage(ColorUtil.colorize(
                        "&6[VaultCrates] &eMilestone erreicht: &f" + ms.getId() + " &e(" + crate + ")"));
            }
        }
    }

    private void deliverReward(Player player, Reward reward) {
        // Give main item
        if (reward.isGiveItem()) {
            ItemStack item = reward.buildDisplayItem();
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        // Execute reward commands
        for (String cmd : reward.getCommands()) {
            String resolved = reward.replaceVariables(cmd, player);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        // Execute custom enchantment plugin commands (run after GiveItem so the item is in the inventory)
        for (String cmd : reward.getEnchantCommands()) {
            String resolved = reward.replaceVariables(cmd, player);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        // Messages to player
        for (String msg : reward.getMessagesToPlayer()) {
            player.sendMessage(ColorUtil.colorize(reward.replaceVariables(msg, player)));
        }

        // Give bundled extra items
        for (BundledItem bi : reward.getBundledItems()) {
            ItemStack biItem = bi.buildItemStack();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(biItem);
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        // Broadcast messages (per-reward config)
        for (String msg : reward.getBroadcastMessages()) {
            String resolved = ColorUtil.colorize(reward.replaceVariables(msg, player));
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(resolved));
        }
    }

    /**
     * Broadcasts the crate's final message to all online players,
     * with reward placeholders replaced.
     */
    protected void sendFinalMessage(AnimationSession session) {
        Player player = session.getPlayer();
        if (player == null) return;

        List<Reward> prizes = session.getSelectedRewards();
        Reward best = session.getBestReward();

        for (String line : session.getCrate().getFinalMessage()) {
            String result = line.replace("{player_name}", player.getName());

            // Replace {ColoredReward:N} and {Reward:N}
            for (int i = 0; i < prizes.size(); i++) {
                String coloredName = ColorUtil.colorize(prizes.get(i).getName());
                String plainName = ColorUtil.strip(prizes.get(i).getName());
                result = result
                        .replace("{ColoredReward:" + (i + 1) + "}", coloredName)
                        .replace("{Reward:" + (i + 1) + "}", plainName);
            }
            // Best reward
            if (best != null) {
                result = result
                        .replace("{ColoredReward:best}", ColorUtil.colorize(best.getName()))
                        .replace("{Reward:best}", ColorUtil.strip(best.getName()));
            }

            String finalResult = ColorUtil.colorize(result);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalResult));
        }
    }

    /**
     * Sends a private win message to the player (only they can see it).
     * Template is read from {@code config.yml → Win_Message}.
     * Supports: {@code {reward_name}}, {@code {rarity}}, {@code {crate_name}}, {@code {player_name}}.
     */
    protected void sendWinMessage(AnimationSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) return;

        String template = plugin.getConfigManager().getString("Win_Message", "");
        if (template == null || template.isBlank()) return;

        List<Reward> prizes = session.getSelectedRewards();
        Reward main = prizes.isEmpty() ? session.getBestReward() : prizes.get(0);
        if (main == null) return;

        Rarity rarity = main.getRarity();
        String msg = template
                .replace("{reward_name}", ColorUtil.colorize(main.getName()))
                .replace("{rarity}",      rarity.getDisplayName())
                .replace("{crate_name}",  ColorUtil.colorize(session.getCrate().getName()))
                .replace("{player_name}", player.getName());

        player.sendMessage(ColorUtil.colorize(msg));
    }

    /**
     * Called by animation implementations when the animation is fully complete.
     * Delivers rewards, sends the final message, cleans up and notifies the manager.
     */
    protected void finishAnimation(AnimationSession session) {
        if (session.isFinished()) return;
        session.setFinished(true);

        giveRewards(session);
        sendWinMessage(session);
        if (!session.getCrate().getFinalMessage().isEmpty()) {
            sendFinalMessage(session);
        }
        session.cleanup();

        // Notify manager to remove the session
        plugin.getAnimationManager().onAnimationFinished(session);

        // Respawn the hologram
        Location loc = session.getCrateLocation();
        if (!session.getCrate().getHologramLines().isEmpty()) {
            plugin.getHologramManager().spawnHologram(
                    plugin.getHologramManager().locationToId(loc),
                    loc,
                    session.getCrate().getHologramLines());
        }
    }
}
