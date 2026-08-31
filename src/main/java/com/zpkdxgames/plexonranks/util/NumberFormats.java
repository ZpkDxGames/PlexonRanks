package com.zpkdxgames.plexonranks.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberFormats {
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat DECIMAL = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private NumberFormats() {
    }

    public static synchronized String number(double value) {
        return Math.rint(value) == value ? WHOLE.format(value) : DECIMAL.format(value);
    }

    public static String durationMinutes(double minutes) {
        long total = Math.max(0L, Math.round(minutes));
        long days = total / 1440;
        long hours = (total % 1440) / 60;
        long mins = total % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}

