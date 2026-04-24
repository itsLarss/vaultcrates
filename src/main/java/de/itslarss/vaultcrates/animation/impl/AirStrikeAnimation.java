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
import java.util.concurrent.ThreadLocalRandom;

/**
 * AirStrike animation — items fall from the sky with lightning strikes.
 */
public class AirStrikeAnimation extends CrateAnimation {

    private static final int SKY_HEIGHT = 20;

    public AirStrikeAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.AIRSTRIKE; }

    @Override
    public void start(AnimationSession session) {
        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);
        List<Reward> prizes = session.getSelectedRewards();

        // We'll drop items one by one every 30 ticks, then finish
        int[] strikeIndex = {0};
        int totalStrikes = prizes.size();
        int[] tick = {0};
        int strikeIntervalTicks = 30;

        // Pre-spawn items high above
        List<ItemDisplay> displays = new ArrayList<>();
        for (Reward prize : prizes) {
            double offX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
            double offZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
            Location skyLoc = center.clone().add(offX, SKY_HEIGHT, offZ);
            displays.add(spawnItemDisplay(session, skyLoc, prize.buildDisplayItem(), Display.Billboard.VERTICAL));
        }

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;

            // Move all items downward
            for (ItemDisplay d : displays) {
                if (d.isValid()) {
                    Location loc = d.getLocation();
                    double crateY = center.getY();
                    if (loc.getY() > crateY + 0.5) {
                        d.teleport(loc.clone().subtract(0, 0.3, 0));
                        // Trail
                        if (tick[0] % 2 == 0)
                            d.getWorld().spawnParticle(Particle.FLAME, loc, 2, 0.05, 0.05, 0.05, 0.01);
                    }
                }
            }

            // Strike a lightning per interval
            if (tick[0] % strikeIntervalTicks == 0 && strikeIndex[0] < totalStrikes) {
                ItemDisplay d = displays.get(strikeIndex[0]);
                if (d.isValid()) {
                    Location impact = center.clone().add(
                            d.getLocation().getX() - center.getX(),
                            0,
                            d.getLocation().getZ() - center.getZ());
                    strikeLightningEffect(impact);
                    playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f);
                    spawnParticles(impact.clone().add(0, 1, 0), Particle.EXPLOSION_EMITTER, 1, 0);
                }
                strikeIndex[0]++;
            }

            if (strikeIndex[0] >= totalStrikes && tick[0] > strikeIndex[0] * strikeIntervalTicks + 20) {
                finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
