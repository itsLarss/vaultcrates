package de.itslarss.vaultcrates.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for number formatting and random number generation.
 */
public final class NumberUtil {

    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,##0.##");

    private NumberUtil() {}

    /**
     * Formats a large number using K, M, B, T suffixes.
     * e.g. 1500 -> "1.5K", 2300000 -> "2.3M"
     */
    public static String formatBig(long n) {
        if (n < 1_000) return String.valueOf(n);
        if (n < 1_000_000) return format1dp(n / 1_000.0) + "K";
        if (n < 1_000_000_000) return format1dp(n / 1_000_000.0) + "M";
        if (n < 1_000_000_000_000L) return format1dp(n / 1_000_000_000.0) + "B";
        return format1dp(n / 1_000_000_000_000.0) + "T";
    }

    /**
     * Returns a random integer between min (inclusive) and max (inclusive).
     */
    public static int randomInt(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Returns a random double between min (inclusive) and max (exclusive).
     */
    public static double randomDouble(double min, double max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * Rounds a double to the given number of decimal places.
     */
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("Decimal places must be >= 0");
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Formats a number with commas and up to 2 decimal places.
     * e.g. 10000.5 -> "10,000.5"
     */
    public static String formatNumber(double d) {
        return COMMA_FORMAT.format(d);
    }

    /**
     * Parses an integer safely, returning the default on failure.
     */
    public static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static String format1dp(double value) {
        long rounded = Math.round(value * 10);
        if (rounded % 10 == 0) return String.valueOf(rounded / 10);
        return (rounded / 10) + "." + (rounded % 10);
    }
}
