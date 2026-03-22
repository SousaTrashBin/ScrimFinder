package fc.ul.scrimfinder.util;

public class TimeConverter {
    public static double millisecondsToMinutes(long milliseconds) {
        return (double) (milliseconds / 1000) / 60;
    }
}
