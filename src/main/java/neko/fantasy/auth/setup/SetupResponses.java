package neko.fantasy.auth.setup;

public final class SetupResponses {

    private SetupResponses() {
    }

    public record Status(boolean required, boolean tokenRequired) {
    }

    public record Result(String adminUsername, boolean restartRequired, String message) {
    }
}
