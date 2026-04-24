package de.itslarss.vaultcrates.animation.impl;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationSession;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.CrateAnimation;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.util.ColorUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Round animation — one item cycles through all prizes, slows down and lands on the winner.
 * A ▼ arrow indicator shows exactly where the result will land.
 * A name label above the item updates in real-time.
 */
public class RoundAnimation extends CrateAnimation {

    public RoundAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.ROUND; }

    /** Duration in seconds. Subclasses can override. */
    protected int durationSeconds() { return plugin.getConfigManager().getInt("Animations.Round.Length", 8); }

    /** Height above the crate block. */
    protected double itemHeight() { return plugin.getConfigManager().getDouble("Animations.Round.Height", 2.5); }

    @Override
    public void start(AnimationSession session) {
        boolean lightning = plugin.getConfigManager().getBoolean("Animations.Round.Lightning", true);
        int totalTicks   = durationSeconds() * 20;
        int slowStart    = totalTicks - 60; // begin slowing 3 s before end

        Location center  = session.getCrateLocation().clone().add(0.5, 0, 0.5);
        Location itemLoc = center.clone().add(0, itemHeight(), 0);

        // Determine winner (first selected prize)
        List<Reward> selected = session.getSelectedRewards();
        Reward winner = selected.isEmpty() ? null : selected.get(0);

        // Pool of prizes to cycle through (shuffled for variety)
        // Must be effectively final for lambda capture — build via temp variable
        List<Reward> poolTemp = new ArrayList<>(session.getCrate().getPrizes());
        if (poolTemp.isEmpty()) poolTemp = new ArrayList<>(selected);
        Collections.shuffle(poolTemp);
        final List<Reward> pool = poolTemp;

        // ── Entities ─────────────────────────────────────────────────────────

        // 1. The single spinning item display
        final ItemDisplay itemDisplay = spawnItemDisplay(session, itemLoc,
                pool.get(0).buildDisplayItem(), Display.Billboard.CENTER);

        // 2. Name label just above the item
        final TextDisplay nameLabel = spawnTextDisplay(session,
                itemLoc.clone().add(0, 0.65, 0),
                coloredName(pool.get(0)));
        nameLabel.setBackgroundColor(Color.fromARGB(140, 0, 0, 0));
        nameLabel.setShadowed(true);

        // 3. Fixed ▼ arrow indicator above the name label
        TextDisplay arrow = spawnTextDisplay(session,
                itemLoc.clone().add(0, 1.25, 0),
                "&6&l▼");
        arrow.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // transparent bg

        // ── State ─────────────────────────────────────────────────────────────
        int[]     tick           = {0};
        int[]     lastChange     = {0};
        int[]     poolIdx        = {0};
        int[]     changeInterval = {2}; // ticks between prize switches (starts fast)

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }

            tick[0]++;

            // Ambient particles
            if (tick[0] % 6 == 0) {
                spawnParticles(itemLoc, Particle.ENCHANTED_HIT, 2, 0.25);
            }

            // ── Slow down: increase change interval gradually ──────────────
            if (tick[0] >= slowStart) {
                int elapsed = tick[0] - slowStart;
                // linear: 2 → 35 over 60 ticks
                changeInterval[0] = 2 + (elapsed * 33 / 60);
            }

            // ── Cycle prize items ──────────────────────────────────────────
            boolean endPhase = tick[0] >= totalTicks - 8;
            if (!endPhase && tick[0] - lastChange[0] >= changeInterval[0]) {
                lastChange[0]  = tick[0];
                poolIdx[0]     = (poolIdx[0] + 1) % pool.size();
                Reward current = pool.get(poolIdx[0]);

                itemDisplay.setItemStack(current.buildDisplayItem());
                updateLabel(nameLabel, current);

                // Click pitch rises as it slows — satisfying "slot machine" feel
                float pitch = 0.8f + (float) tick[0] / totalTicks * 0.6f;
                playSound(center, Sound.UI_BUTTON_CLICK, 0.4f, pitch);
            }

            // ── Lock to winner 8 ticks before end ─────────────────────────
            if (tick[0] == totalTicks - 8 && winner != null) {
                itemDisplay.setItemStack(winner.buildDisplayItem());
                updateLabel(nameLabel, winner);
                applyRarityGlow(itemDisplay, winner);
                playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            }

            // ── Finish ────────────────────────────────────────────────────
            if (tick[0] >= totalTicks) {
                if (lightning) strikeLightningEffect(itemLoc);
                playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
                finishAnimation(session);
            }
        }, 1L, 1L);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    protected String coloredName(Reward reward) {
        return ColorUtil.colorize(reward.getName());
    }

    protected void updateLabel(TextDisplay label, Reward reward) {
        label.text(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(ColorUtil.colorize(reward.getName())));
    }
}
