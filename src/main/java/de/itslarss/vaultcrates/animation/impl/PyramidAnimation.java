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
 * Pyramid animation — all crate prizes arranged in a three-tier rotating pyramid.
 */
public class PyramidAnimation extends CrateAnimation {

    public PyramidAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.PYRAMID; }

    @Override
    public void start(AnimationSession session) {
        int durationTicks = 10 * 20;
        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);

        // Use all crate prizes across the three tiers
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        List<ItemDisplay> displays = new ArrayList<>();

        // Base ring — 4 positions
        int baseCount = Math.min(4, allPrizes.size());
        for (int i = 0; i < baseCount; i++) {
            double angle = (2 * Math.PI / 4) * i;
            Location loc = center.clone().add(Math.cos(angle) * 1.5, 1.5, Math.sin(angle) * 1.5);
            displays.add(spawnItemDisplay(session, loc,
                    allPrizes.get(i % allPrizes.size()).buildDisplayItem(), Display.Billboard.VERTICAL));
        }

        // Mid ring — 4 positions (offset angle, counter-rotates)
        for (int i = 0; i < 4; i++) {
            double angle = (2 * Math.PI / 4) * i + (Math.PI / 4);
            Location loc = center.clone().add(Math.cos(angle) * 1.0, 2.5, Math.sin(angle) * 1.0);
            int idx = (baseCount + i) % allPrizes.size();
            displays.add(spawnItemDisplay(session, loc,
                    allPrizes.get(idx).buildDisplayItem(), Display.Billboard.VERTICAL));
        }

        // Apex — winner / best reward
        Reward apexReward = session.getBestReward() != null
                ? session.getBestReward()
                : (session.getSelectedRewards().isEmpty() ? null : session.getSelectedRewards().get(0));
        ItemStack topItem = apexReward != null ? apexReward.buildDisplayItem() : new ItemStack(Material.NETHER_STAR);
        ItemDisplay apex = spawnItemDisplay(session, center.clone().add(0, 4, 0), topItem, Display.Billboard.FIXED);
        displays.add(apex);

        double[]  angle        = {0.0};
        int[]     tick         = {0};
        boolean[] labelSpawned = {false};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;
            angle[0] += 2.0 * (Math.PI / 180.0);

            for (int i = 0; i < baseCount; i++) {
                ItemDisplay d = displays.get(i);
                if (!d.isValid()) continue;
                double a = angle[0] + (2 * Math.PI / 4) * i;
                d.teleport(center.clone().add(Math.cos(a) * 1.5, 1.5, Math.sin(a) * 1.5));
            }
            for (int i = 0; i < 4; i++) {
                ItemDisplay d = displays.get(baseCount + i);
                if (!d.isValid()) continue;
                double a = -angle[0] + (2 * Math.PI / 4) * i + (Math.PI / 4);
                d.teleport(center.clone().add(Math.cos(a) * 1.0, 2.5, Math.sin(a) * 1.0));
            }

            if (tick[0] % 8 == 0) spawnParticles(center.clone().add(0, 3, 0), Particle.ENCHANT, 10, 1.5);

            // Winner label + glow 2 s before end
            if (tick[0] == durationTicks - 40 && !labelSpawned[0] && apexReward != null) {
                labelSpawned[0] = true;
                applyRarityGlow(apex, apexReward);
                spawnWinnerLabel(session, center.clone().add(0, 5.5, 0), apexReward);
                strikeLightningEffect(center.clone().add(0, 5, 0));
                playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
            }
            if (tick[0] >= durationTicks) finishAnimation(session);
        }, 1L, 1L);
    }
}
