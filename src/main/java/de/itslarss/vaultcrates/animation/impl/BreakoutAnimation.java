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

import java.util.ArrayList;
import java.util.List;

/**
 * Breakout animation — all crate prizes burst outward from the crate then arc back to centre.
 */
public class BreakoutAnimation extends CrateAnimation {

    public BreakoutAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.BREAKOUT; }

    @Override
    public void start(AnimationSession session) {
        Location center = session.getCrateLocation().clone().add(0.5, 1, 0.5);

        // Show all crate prizes
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        int count = allPrizes.size();
        List<double[]> directions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = (2 * Math.PI / count) * i;
            directions.add(new double[]{Math.cos(a) * 0.15, 0.1, Math.sin(a) * 0.15});
        }

        List<ItemDisplay> displays = new ArrayList<>();
        for (Reward prize : allPrizes) {
            displays.add(spawnItemDisplay(session, center.clone(), prize.buildDisplayItem(), Display.Billboard.VERTICAL));
        }

        // Winner for label
        Reward winner = session.getSelectedRewards().isEmpty()
                ? (allPrizes.isEmpty() ? null : allPrizes.get(0))
                : session.getSelectedRewards().get(0);

        int[]     phase        = {0};
        int[]     tick         = {0};
        boolean[] labelSpawned = {false};
        int burstTicks = 20, floatTicks = 60, returnTicks = 30;

        playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 0.8f);
        spawnParticles(center, Particle.ENCHANTED_HIT, 15, 0.5);

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;

            if (phase[0] == 0) { // burst outward
                for (int i = 0; i < displays.size(); i++) {
                    ItemDisplay d = displays.get(i);
                    if (!d.isValid()) continue;
                    double[] dir = directions.get(i);
                    d.teleport(d.getLocation().clone().add(dir[0], dir[1], dir[2]));
                    d.getWorld().spawnParticle(Particle.FLAME, d.getLocation(), 1, 0, 0, 0, 0);
                }
                if (tick[0] >= burstTicks) { phase[0] = 1; tick[0] = 0; }

            } else if (phase[0] == 1) { // float — show winner label
                if (tick[0] % 10 == 0) spawnParticles(center, Particle.ENCHANT, 5, 1.5);
                if (tick[0] == 10 && !labelSpawned[0] && winner != null) {
                    labelSpawned[0] = true;
                    spawnWinnerLabel(session, center.clone().add(0, 4.0, 0), winner);
                }
                if (tick[0] >= floatTicks) { phase[0] = 2; tick[0] = 0; }

            } else { // return to center
                for (ItemDisplay d : displays) {
                    if (!d.isValid()) continue;
                    Location cur = d.getLocation();
                    double dx = (center.getX() - cur.getX()) * 0.15;
                    double dy = (center.getY() - cur.getY()) * 0.15;
                    double dz = (center.getZ() - cur.getZ()) * 0.15;
                    d.teleport(cur.clone().add(dx, dy, dz));
                }
                if (tick[0] >= returnTicks) {
                    strikeLightningEffect(center);
                    playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
                    finishAnimation(session);
                }
            }
        }, 1L, 1L);
    }
}
