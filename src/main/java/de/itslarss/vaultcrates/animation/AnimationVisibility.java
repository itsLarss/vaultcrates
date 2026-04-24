package de.itslarss.vaultcrates.animation;

/**
 * Controls who can see a crate animation when it is playing.
 */
public enum AnimationVisibility {

    /** Only the player opening the crate sees the animation. */
    INDIVIDUAL,

    /** All players on the server see the animation. */
    ALL,

    /**
     * Players far enough away see the animation; players within
     * the configured proximity distance do not (except the opener).
     * Useful so multiple people can open crates simultaneously without visual interference.
     */
    PROXIMITY;

    /**
     * Parses a visibility option from a config string.
     *
     * @param s the config string (case-insensitive)
     * @return the matching visibility, defaulting to {@link #PROXIMITY}
     */
    public static AnimationVisibility fromString(String s) {
        if (s == null) return PROXIMITY;
        return switch (s.toUpperCase().trim()) {
            case "INDIVIDUAL" -> INDIVIDUAL;
            case "ALL" -> ALL;
            default -> PROXIMITY;
        };
    }
}
