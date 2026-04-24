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

import java.util.List;

/**
 * Display animation — shows a showcase item that cycles through prizes over time.
 */
public class DisplayAnimation extends CrateAnimation {

    public DisplayAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.DISPLAY; }

    @Override
    public void start(AnimationSession session) {
        int lengthSec    = plugin.getConfigManager().getInt("Animations.Display.Length", 14);
        int changeIntervalSec = plugin.getConfigManager().getInt("Animations.Display.Item_Change_Interval", 3);
        boolean waterDrops = plugin.getConfigManager().getBoolean("Animations.Display.Water_Drops", true);
        int changeIntervalTicks = changeIntervalSec * 20;
        int totalTicks = lengthSec * 20;

        Location center = session.getCrateLocation().clone().add(0.5, 0, 0.5);
        Location displayLoc = center.clone().add(0, 2.5, 0);

        List<Reward> prizes = session.getSelectedRewards();
        if (prizes.isEmpty()) { finishAnimation(session); return; }

        // Spawn central display
        ItemDisplay display = spawnItemDisplay(session, displayLoc,
                prizes.get(0).buildDisplayItem(), Display.Billboard.FIXED);

        int[] tick = {0};
        int[] prizeIndex = {0};

        runRepeating(session, () -> {
            Player player = session.getPlayer();
            if (player == null || !player.isOnline()) { finishAnimation(session); return; }

            tick[0]++;

            // Water/lava drops
            if (waterDrops && tick[0] % 5 == 0) {
                spawnParticles(displayLoc.clone().add(0, 0.5, 0), Particle.DRIPPING_WATER, 3, 0.3);
            }

            // Change displayed item
            if (tick[0] % changeIntervalTicks == 0 && display.isValid()) {
                prizeIndex[0] = (prizeIndex[0] + 1) % prizes.size();
                display.setItemStack(prizes.get(prizeIndex[0]).buildDisplayItem());
                playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                spawnParticles(displayLoc, Particle.HAPPY_VILLAGER, 8, 0.3);
            }

            // Ambient particles
            if (tick[0] % 10 == 0) {
                spawnParticles(center.clone().add(0, 1.5, 0), Particle.ENCHANT, 5, 1.0);
            }

            if (tick[0] >= totalTicks) {
                if (display.isValid()) {
                    // Show the actual winner
                    display.setItemStack(prizes.get(0).buildDisplayItem());
                }
                spawnParticles(center.clone().add(0, 2.5, 0), Particle.FIREWORK, 30, 0.5);
                playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                finishAnimation(session);
            }
        }, 1L, 1L);
    }
}
