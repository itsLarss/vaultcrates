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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Contraband animation — all crate prizes float up one by one, then dissolve except the winner(s).
 */
public class ContrabandAnimation extends CrateAnimation {

    public ContrabandAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.CONTRABAND; }

    @Override
    public void start(AnimationSession session) {
        double floatSpeed        = plugin.getConfigManager().getDouble("Animations.Contraband.Float_Speed", 1.5);
        double spawnInterval     = plugin.getConfigManager().getDouble("Animations.Contraband.Spawn_Interval", 0.65);
        double disappearInterval = plugin.getConfigManager().getDouble("Animations.Contraband.Disappear_Interval", 0.65);
        double crateHeight       = plugin.getConfigManager().getDouble("Animations.Contraband.Height", 2.0);

        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);

        // Float ALL crate prizes; winners are the selected rewards
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        List<Reward> winners = new ArrayList<>(session.getSelectedRewards());
        if (winners.isEmpty() && !allPrizes.isEmpty()) winners.add(allPrizes.get(0));

        long spawnIntervalTicks   = Math.max(1, (long)(spawnInterval * 20));
        long disappearIntervalTicks = Math.max(1, (long)(disappearInterval * 20));
        long totalTicks = (long)((allPrizes.size() * spawnInterval + 3.0
                + allPrizes.size() * disappearInterval) * 20) + 80;

        // Winner for label
        Reward winner = winners.isEmpty() ? (allPrizes.isEmpty() ? null : allPrizes.get(0)) : winners.get(0);

        List<ItemDisplay> spawnedDisplays = new ArrayList<>();
        long[]    nextSpawnTick  = {spawnIntervalTicks};
        int[]     spawnedCount   = {0};
        int[]     tick           = {0};
        boolean[] disappearing   = {false};
        int[]     disappearIdx   = {0};
        boolean[] labelSpawned   = {false};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;

            // Spawn all prizes one by one
            if (!disappearing[0] && spawnedCount[0] < allPrizes.size() && tick[0] >= nextSpawnTick[0]) {
                Reward r = allPrizes.get(spawnedCount[0]);
                double offX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
                double offZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
                Location loc = center.clone().add(offX, crateHeight, offZ);
                spawnedDisplays.add(spawnItemDisplay(session, loc, r.buildDisplayItem(), Display.Billboard.VERTICAL));
                spawnedCount[0]++;
                nextSpawnTick[0] = tick[0] + spawnIntervalTicks;
                playSound(center, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
            }

            // Float all upward
            for (ItemDisplay d : spawnedDisplays) {
                if (d.isValid()) d.teleport(d.getLocation().clone().add(0, floatSpeed * 0.01, 0));
            }

            if (tick[0] % 5 == 0)
                spawnParticles(center.clone().add(0, crateHeight, 0), Particle.SMOKE, 3, 0.5);

            // Begin disappearing 60 ticks after last spawn
            if (!disappearing[0] && spawnedCount[0] >= allPrizes.size()
                    && tick[0] >= nextSpawnTick[0] + 60) {
                disappearing[0] = true;
                // Spawn winner label when dissolve phase begins
                if (!labelSpawned[0] && winner != null) {
                    labelSpawned[0] = true;
                    spawnWinnerLabel(session, center.clone().add(0, crateHeight + 2.5, 0), winner);
                }
            }

            // Remove non-winners one by one, keep winners until last
            if (disappearing[0] && tick[0] % disappearIntervalTicks == 0) {
                int toRemove = spawnedDisplays.size() - winners.size();
                if (disappearIdx[0] < toRemove) {
                    ItemDisplay d = spawnedDisplays.get(disappearIdx[0]++);
                    if (d.isValid()) {
                        d.getWorld().spawnParticle(Particle.SMOKE, d.getLocation(), 8, 0.2, 0.2, 0.2, 0);
                        d.remove();
                    }
                } else {
                    // All extras gone
                    finishAnimation(session);
                    return;
                }
            }

            if (tick[0] >= totalTicks) finishAnimation(session);
        }, 1L, 1L);
    }
}
