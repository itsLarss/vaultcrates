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
 * Round2 animation — mystery boxes orbit the crate; halfway through, lightning reveals each item.
 */
public class Round2Animation extends CrateAnimation {

    public Round2Animation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.ROUND2; }

    @Override
    public void start(AnimationSession session) {
        int durationSec   = plugin.getConfigManager().getInt("Animations.Round2.Length", 12);
        double speed      = plugin.getConfigManager().getDouble("Animations.Round2.Speed", 3.0);
        boolean useLightning = plugin.getConfigManager().getBoolean("Animations.Round2.Lightning", true);
        int durationTicks = durationSec * 20;
        int revealTick    = durationTicks / 2;

        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);
        List<Reward> prizes = session.getSelectedRewards();
        int size = Math.max(prizes.size(), 8);

        // Spawn mystery boxes (barrier items)
        List<ItemDisplay> displays = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double angle = (2 * Math.PI / size) * i;
            Location loc = center.clone().add(Math.cos(angle) * 2.0, 3.0, Math.sin(angle) * 2.0);
            displays.add(spawnItemDisplay(session, loc, new ItemStack(Material.BARRIER), Display.Billboard.VERTICAL));
        }

        double[] angle = {0.0};
        double[] currentSpeed = {speed};
        int[] tick = {0};
        boolean[] revealed = {false};
        int[] revealIndex = {0};
        long nextRevealTick = revealTick;
        long[] nextRevealTickArr = {nextRevealTick};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }
            tick[0]++;
            angle[0] += currentSpeed[0] * (Math.PI / 180.0);

            // Update positions
            for (int i = 0; i < displays.size(); i++) {
                ItemDisplay d = displays.get(i);
                if (!d.isValid()) continue;
                double a = angle[0] + (2 * Math.PI / displays.size()) * i;
                d.teleport(center.clone().add(Math.cos(a) * 2.0, 3.0, Math.sin(a) * 2.0));
            }

            // Reveal items one by one after halfway point
            if (tick[0] >= revealTick && revealIndex[0] < prizes.size()
                    && tick[0] >= nextRevealTickArr[0]) {
                ItemDisplay d = displays.get(revealIndex[0]);
                if (d.isValid()) {
                    d.setItemStack(prizes.get(revealIndex[0]).buildDisplayItem());
                    if (useLightning) {
                        strikeLightningEffect(d.getLocation());
                    }
                    playSound(d.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.8f);
                    spawnParticles(d.getLocation(), Particle.ENCHANT, 10, 0.3);
                }
                revealIndex[0]++;
                nextRevealTickArr[0] = tick[0] + 15;
            }

            // Particles
            if (tick[0] % 5 == 0) {
                spawnParticles(center.clone().add(0, 3, 0), Particle.ENCHANT, 3, 1.5);
            }

            // Slow down near end
            if (tick[0] >= durationTicks - 40 && currentSpeed[0] > 0.3) currentSpeed[0] *= 0.95;

            if (tick[0] >= durationTicks) {
                strikeLightningEffect(center.clone().add(0, 3, 0));
                playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.0f);
                finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
