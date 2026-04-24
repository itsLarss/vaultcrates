package de.itslarss.vaultcrates.animation.impl;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationSession;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.CrateAnimation;
import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmic animation — all crate prizes orbit in two counter-rotating rings around a central item.
 * Uses ENCHANTED_HIT + ENCHANT particles (safe on all 1.21.x versions — no data class needed).
 * Winner label and rarity glow appear 3 seconds before the end.
 */
public class CosmicAnimation extends CrateAnimation {

    public CosmicAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.COSMIC; }

    @Override
    public void start(AnimationSession session) {
        int durationSec   = plugin.getConfigManager().getInt("Animations.Round.Length", 10);
        double speed      = plugin.getConfigManager().getDouble("Animations.Round.Speed", 2.5);
        int durationTicks = durationSec * 20;

        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);

        // Show ALL crate prizes across both rings
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        int halfSize = Math.max(allPrizes.size() / 2, 4);

        // Outer ring (larger radius, lower height)
        List<ItemDisplay> outerRing = new ArrayList<>();
        for (int i = 0; i < halfSize; i++) {
            double angle = (2 * Math.PI / halfSize) * i;
            Location loc = center.clone().add(Math.cos(angle) * 2.5, 2.5, Math.sin(angle) * 2.5);
            ItemStack item = allPrizes.get(i % allPrizes.size()).buildDisplayItem();
            outerRing.add(spawnItemDisplay(session, loc, item, Display.Billboard.VERTICAL));
        }

        // Inner ring (smaller radius, higher up, counter-rotates)
        List<ItemDisplay> innerRing = new ArrayList<>();
        for (int i = 0; i < halfSize; i++) {
            double angle = (2 * Math.PI / halfSize) * i;
            Location loc = center.clone().add(Math.cos(angle) * 1.2, 4.0, Math.sin(angle) * 1.2);
            int prizeIdx = (halfSize + i) % allPrizes.size();
            ItemStack item = allPrizes.get(prizeIdx).buildDisplayItem();
            innerRing.add(spawnItemDisplay(session, loc, item, Display.Billboard.VERTICAL));
        }

        // Central display — winner or best reward
        Reward centerReward = session.getBestReward() != null
                ? session.getBestReward()
                : (session.getSelectedRewards().isEmpty() ? null : session.getSelectedRewards().get(0));
        ItemStack centerItem = centerReward != null
                ? centerReward.buildDisplayItem()
                : new ItemStack(Material.ENDER_CHEST);
        ItemDisplay central = spawnItemDisplay(session,
                center.clone().add(0, 3, 0), centerItem, Display.Billboard.FIXED);

        double[]     outerAngle   = {0.0};
        double[]     innerAngle   = {Math.PI};
        int[]        tick         = {0};
        double[]     currentSpeed = {speed};
        boolean[]    labelSpawned = {false};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }

            tick[0]++;
            outerAngle[0] += currentSpeed[0] * (Math.PI / 180.0);
            innerAngle[0] -= currentSpeed[0] * 1.5 * (Math.PI / 180.0);

            // Update outer ring
            for (int i = 0; i < outerRing.size(); i++) {
                ItemDisplay d = outerRing.get(i);
                if (!d.isValid()) continue;
                double a = outerAngle[0] + (2 * Math.PI / outerRing.size()) * i;
                d.teleport(center.clone().add(Math.cos(a) * 2.5, 2.5, Math.sin(a) * 2.5));
            }

            // Update inner ring
            for (int i = 0; i < innerRing.size(); i++) {
                ItemDisplay d = innerRing.get(i);
                if (!d.isValid()) continue;
                double a = innerAngle[0] + (2 * Math.PI / innerRing.size()) * i;
                d.teleport(center.clone().add(Math.cos(a) * 1.2, 4.0, Math.sin(a) * 1.2));
            }

            // Central bob
            if (central.isValid()) {
                double bobY = 3 + Math.sin(tick[0] * 0.1) * 0.3;
                central.teleport(center.clone().add(0, bobY, 0));
            }

            // Particles (ENCHANTED_HIT is safe on all 1.21.x — no extra data class needed)
            if (tick[0] % 3 == 0) {
                spawnParticles(center.clone().add(0, 3, 0), Particle.ENCHANTED_HIT, 3, 1.2);
                if (tick[0] % 9 == 0)
                    spawnParticles(center.clone().add(0, 3, 0), Particle.ENCHANT, 10, 2.0);
            }

            // Slow down near end + reveal winner label + glow
            if (tick[0] >= durationTicks - 60) {
                if (currentSpeed[0] > 0.3) currentSpeed[0] *= 0.95;
                if (!labelSpawned[0] && centerReward != null) {
                    labelSpawned[0] = true;
                    applyRarityGlow(central, centerReward);
                    spawnWinnerLabel(session, center.clone().add(0, 5.2, 0), centerReward);
                }
            }

            if (tick[0] >= durationTicks) {
                strikeLightningEffect(center.clone().add(0, 4, 0));
                playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.0f);
                playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
                finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
