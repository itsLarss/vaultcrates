package de.itslarss.vaultcrates.animation;

/**
 * All available crate animation types.
 */
public enum AnimationType {

    ROUND,
    COSMIC,
    DISPLAY,
    PYRAMID,
    CONTRABAND,
    INSTANT,
    AIRSTRIKE,
    BREAKOUT,
    METEORSHOWER,
    YINYANG,
    QUICK,
    ROUND2;

    /**
     * Parses an animation type from a config string.
     *
     * @param s the config string (case-insensitive)
     * @return the matching type, defaulting to {@link #ROUND}
     */
    public static AnimationType fromString(String s) {
        if (s == null) return ROUND;
        return switch (s.toUpperCase().trim()) {
            case "COSMIC"       -> COSMIC;
            case "DISPLAY"      -> DISPLAY;
            case "PYRAMID"      -> PYRAMID;
            case "CONTRABAND"   -> CONTRABAND;
            case "INSTANT"      -> INSTANT;
            case "AIRSTRIKE"    -> AIRSTRIKE;
            case "BREAKOUT"     -> BREAKOUT;
            case "METEORSHOWER" -> METEORSHOWER;
            case "YINYANG"      -> YINYANG;
            case "QUICK"        -> QUICK;
            case "ROUND2"       -> ROUND2;
            default             -> ROUND;
        };
    }
}
