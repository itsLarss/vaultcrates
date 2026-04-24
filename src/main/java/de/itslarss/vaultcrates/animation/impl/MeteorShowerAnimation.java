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
 * MeteorShower animation — all crate prizes fall from the sky one by one with flame trails.
 * Impact creates a light smoke/flame burst (no lightning, no TNT, no explosion sound).
 * Winner label + rarity glow shown at the end.
 */
public class MeteorShowerAnimation extends CrateAnimation {

    /** How many items are shown at once (cap to avoid visual overload). */
    private static final int MAX_METEORS     = 8;
    private static final int DROP_HEIGHT     = 16;
    private static final int METEOR_INTERVAL = 40; // ticks between spawns

    public MeteorShowerAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.METEORSHOWER; }

    @Override
    public void start(AnimationSession session) {
        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);

        // Collect all prizes, cycle if fewer than MAX_METEORS
        List<Reward> allPrizes = new ArrayList<>(session.getCrate().getPrizes());
        if (allPrizes.isEmpty()) allPrizes.addAll(session.getSelectedRewards());

        List<Reward> meteors = new ArrayList<>();
        for (int i = 0; i < MAX_METEORS; i++) {
            meteors.add(allPrizes.get(i % allPrizes.size()));
        }
        int count = meteors.size();

        // Determine winner for end label
        Reward winner = session.getSelectedRewards().isEmpty()
                ? (meteors.isEmpty() ? null : meteors.get(0))
                : session.getSelectedRewards().get(0);

        int[]     tick         = {0};
        int[]     fallen       = {0};
        boolean[] labelSpawned = {false};

        List<ItemDisplay> activeDisplays = new ArrayList<>();
        List<Location>    targets        = new ArrayList<>();

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;

            // Spawn next meteor
            if (fallen[0] < count && tick[0] % METEOR_INTERVAL == 1) {
                double offX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 5;
                double offZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 5;
                Location target = center.clone().add(offX, 0.5, offZ);
                Location skyLoc = target.clone().add(0, DROP_HEIGHT, 0);

                ItemDisplay d = spawnItemDisplay(session, skyLoc,
                        meteors.get(fallen[0]).buildDisplayItem(), Display.Billboard.VERTICAL);
                activeDisplays.add(d);
                targets.add(target);
                fallen[0]++;

                playSound(skyLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 0.5f);
            }

            // Move each meteor downward
            for (int i = 0; i < activeDisplays.size(); i++) {
                ItemDisplay d  = activeDisplays.get(i);
                if (!d.isValid()) continue;
                Location target = targets.get(i);
                Location cur    = d.getLocation();

                if (cur.getY() > target.getY() + 0.4) {
                    d.teleport(cur.clone().subtract(0, 0.45, 0));
                    cur.getWorld().spawnParticle(Particle.FLAME, cur, 3, 0.1, 0.1, 0.1, 0.02);
                    cur.getWorld().spawnParticle(Particle.SMOKE, cur, 1, 0.1, 0.1, 0.1, 0);
                } else {
                    // Light impact — smoke + flame burst only, no sound per meteor
                    target.getWorld().spawnParticle(Particle.SMOKE, target, 8, 0.3, 0.2, 0.3, 0.03);
                    target.getWorld().spawnParticle(Particle.FLAME, target, 5, 0.2, 0.1, 0.2, 0.03);
                    playSound(target, Sound.BLOCK_GRAVEL_FALL, 0.3f, 0.8f);
                    d.remove();
                }
            }

            // All done — show winner label, finish quietly (no lightning)
            int endTick = count * METEOR_INTERVAL + DROP_HEIGHT * 2 + 40;
            if (fallen[0] >= count && tick[0] >= endTick) {
                if (!labelSpawned[0] && winner != null) {
                    labelSpawned[0] = true;
                    spawnWinnerLabel(session, center.clone().add(0, 3, 0), winner);
                    playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                    spawnParticles(center.clone().add(0, 1, 0), Particle.ENCHANTED_HIT, 20, 1.0);
                }
                // Small delay so the label is visible before cleanup
                if (tick[0] >= endTick + 40) finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
