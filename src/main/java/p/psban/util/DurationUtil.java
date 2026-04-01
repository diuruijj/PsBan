package p.psban.util;

public final class DurationUtil {
    private DurationUtil() {}

    public static long parse(String input) {
        if (input == null) return -2L;
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) return -2L;
        if (s.equals("0") || s.equals("perm") || s.equals("perma") || s.equals("permanente") || s.equals("forever") || s.equals("inf") || s.equals("infinito") || s.equals("-1")) {
            return 0L;
        }

        long total = 0L;
        StringBuilder number = new StringBuilder();
        boolean consumedUnit = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (number.length() == 0) {
                return -2L;
            }
            long value;
            try {
                value = Long.parseLong(number.toString());
            } catch (NumberFormatException ex) {
                return -2L;
            }
            number.setLength(0);
            long factor = switch (c) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> -1L;
            };
            if (factor < 0L) return -2L;
            total += value * factor;
            consumedUnit = true;
        }

        if (number.length() > 0) {
            if (consumedUnit) {
                return -2L;
            }
            try {
                total += Long.parseLong(number.toString()) * 1000L;
            } catch (NumberFormatException ex) {
                return -2L;
            }
        }

        return total < 0L ? -2L : total;
    }

    public static String format(long millis) {
        if (millis == Long.MAX_VALUE || millis < 0) return "permanente";
        if (millis == 0) return "0s";
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append('d');
        if (hours > 0) out.append(hours).append('h');
        if (minutes > 0) out.append(minutes).append('m');
        if (seconds > 0 || out.length() == 0) out.append(seconds).append('s');
        return out.toString();
    }
}
