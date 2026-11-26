package vn.edu.tdtu.phlbanking.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class CurrencyUtils {

    private static final DecimalFormat VND_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        VND_FORMAT = new DecimalFormat("#,###", symbols);
    }

    private CurrencyUtils() {}

    public static String formatVnd(long amount) {
        return VND_FORMAT.format(amount) + " VNĐ";
    }

    public static String formatSignedVnd(long changeAmount) {
        String prefix = changeAmount >= 0 ? "+" : "-";
        return prefix + VND_FORMAT.format(Math.abs(changeAmount)) + " VNĐ";
    }
}
