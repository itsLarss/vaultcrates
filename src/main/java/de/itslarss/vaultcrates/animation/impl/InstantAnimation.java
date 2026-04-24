package de.itslarss.vaultcrates.animation.impl;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationSession;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.CrateAnimation;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Instant animation — no visual delay, rewards are given immediately with particles and sound.
 */
public class InstantAnimation extends CrateAnimation {

    public InstantAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.INSTANT; }

    @Override
    public void start(AnimationSession session) {
        Location center = session.getCrateLocation().clone().add(0.5, 1, 0.5);

        playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        spawnParticles(center, Particle.HAPPY_VILLAGER, 20, 0.5);
        spawnParticles(center, Particle.FIREWORK, 30, 0.3);

        runLater(session, () -> finishAnimation(session), 1L);
    }
}
