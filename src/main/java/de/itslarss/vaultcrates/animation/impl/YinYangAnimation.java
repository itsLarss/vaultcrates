package de.itslarss.vaultcrates.animation.impl;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationSession;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.CrateAnimation;
import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * YinYang animation — all prizes in two counter-rotating rings that converge to reveal the winner.
 */
public class YinYangAnimation extends CrateAnimation {

    public YinYangAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.YINYANG; }

    @Override
    public void start(AnimationSession session) {
        int durationTicks = 10 * 20;
        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);

        // Show ALL crate prizes across both rings
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        int halfCount = Math.max(allPrizes.size() / 2, 3);

        // Ring 1 — clockwise
        List<ItemDisplay> ring1 = new ArrayList<>();
        for (int i = 0; i < halfCount; i++) {
            double angle = (2 * Math.PI / halfCount) * i;
            Location loc = center.clone().add(Math.cos(angle) * 2.0, 3.0, Math.sin(angle) * 2.0);
            ItemStack item = allPrizes.get(i % allPrizes.size()).buildDisplayItem();
            ring1.add(spawnItemDisplay(session, loc, item, Display.Billboard.VERTICAL));
        }

        // Ring 2 — counter-clockwise
        List<ItemDisplay> ring2 = new ArrayList<>();
        for (int i = 0; i < halfCount; i++) {
            double angle = (2 * Math.PI / halfCount) * i + Math.PI;
            Location loc = center.clone().add(Math.cos(angle) * 2.0, 2.0, Math.sin(angle) * 2.0);
            int idx = (halfCount + i) % allPrizes.size();
            ItemStack item = allPrizes.get(idx).buildDisplayItem();
            ring2.add(spawnItemDisplay(session, loc, item, Display.Billboard.VERTICAL));
        }

        // Winner for label
        Reward winner = session.getSelectedRewards().isEmpty()
                ? (allPrizes.isEmpty() ? null : allPrizes.get(0))
                : session.getSelectedRewards().get(0);

        double[]  angle        = {0.0};
        int[]     tick         = {0};
        double[]  radius       = {2.0};
        boolean[] labelSpawned = {false};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;

            angle[0] += 2.5 * (Math.PI / 180.0);

            if (tick[0] >= durationTicks - 60 && radius[0] > 0.1)
                radius[0] = Math.max(0.1, radius[0] - 0.03);

            for (int i = 0; i < ring1.size(); i++) {
                ItemDisplay d = ring1.get(i);
                if (!d.isValid()) continue;
                double a = angle[0] + (2 * Math.PI / ring1.size()) * i;
                d.teleport(center.clone().add(
                        Math.cos(a) * radius[0],
                        3.0 - (2.0 - radius[0]) * 0.5,
                        Math.sin(a) * radius[0]));
            }
            for (int i = 0; i < ring2.size(); i++) {
                ItemDisplay d = ring2.get(i);
                if (!d.isValid()) continue;
                double a = -angle[0] + (2 * Math.PI / ring2.size()) * i + Math.PI;
                d.teleport(center.clone().add(
                        Math.cos(a) * radius[0],
                        2.0 + (2.0 - radius[0]) * 0.5,
                        Math.sin(a) * radius[0]));
            }

            if (tick[0] % 5 == 0)
                spawnParticles(center.clone().add(0, 2.5, 0), Particle.ENCHANT, 5, radius[0]);

            // Winner label when rings start converging
            if (tick[0] == durationTicks - 60 && !labelSpawned[0] && winner != null) {
                labelSpawned[0] = true;
                spawnWinnerLabel(session, center.clone().add(0, 4.5, 0), winner);
            }

            if (tick[0] >= durationTicks) {
                strikeLightningEffect(center.clone().add(0, 3, 0));
                playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.2f);
                finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
