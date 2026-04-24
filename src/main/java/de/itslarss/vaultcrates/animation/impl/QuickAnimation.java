package de.itslarss.vaultcrates.animation.impl;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationType;

/**
 * Quick animation — same as Round but only 3 seconds long.
 */
public class QuickAnimation extends RoundAnimation {

    public QuickAnimation(VaultCrates plugin) { super(plugin); }

    @Override public AnimationType getType() { return AnimationType.QUICK; }

    @Override protected int durationSeconds() { return 3; }

    @Override protected double itemHeight() { return 2.5; }
}
