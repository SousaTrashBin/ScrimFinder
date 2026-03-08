package fc.ul.scrimfinder.util;

public class ErrorResponse {

    public String code;
    public String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}