package fc.ul.scrimfinder.util;

public class ColoredMessage {
    public static String withColor(String message, LogColor color) {
        return String.format("%s%s%s", color.getColor(), message, LogColor.RESET.getColor());
    }
}
