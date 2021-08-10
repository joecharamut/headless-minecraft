package rocks.spaghetti.headlessmc.client.lib;

import rocks.spaghetti.headlessmc.game.GameClient;
import rocks.spaghetti.headlessmc.game.SettableInput;

import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GameTextParser {
    private final GameClient client;

    public GameTextParser(GameClient client) {
        this.client = client;
    }

    public ParseResult parse(String command) {
        String[] args = command.split(" ");

        try {
            return switch (args[0].toLowerCase()) {
                case "inv" -> {
                    String inv = client.player.getInventory().main.stream()
                            .map(item -> item.getCount() + " " + item.getItem().getName().getString())
                            .collect(Collectors.joining(", "));

                    yield new ParseResult(ParseResult.Status.SUCCESS, "Your inventory contains: " + inv);
                }

                case "respawn" -> {
                    client.player.requestRespawn();

                    yield new ParseResult(ParseResult.Status.SUCCESS, "Requested respawn");
                }

                case "jump" -> {
                    SettableInput input = ((SettableInput) client.player.input);
                    if (args.length > 1) {
                        boolean jump = Boolean.parseBoolean(args[1]);
                        input.jumping(jump);
                        yield new ParseResult(ParseResult.Status.SUCCESS, "Set jump state to " + jump);
                    } else {
                        boolean last = input.jumping;
                        input.jumping(!last);
                        yield new ParseResult(ParseResult.Status.SUCCESS, "Toggled jump state (now: " + !last + ")");
                    }
                }

                case "walk" -> {
                    float forwards = 0.0F;
                    float sideways = 0.0F;

                    for (int i = 1; i < args.length; i++) {
                        switch (args[i].toLowerCase()) {
                            case "forward" -> forwards = 1.0F;
                            case "backward" -> forwards = -1.0F;
                            case "right" -> sideways = 1.0F;
                            case "left" -> sideways = -1.0F;
                        }
                    }

                    ((SettableInput) client.player.input).movement(forwards, sideways);

                    yield new ParseResult(ParseResult.Status.SUCCESS, args.length > 1 ? "Applied movement" : "Cleared movement");
                }

                default -> new ParseResult(ParseResult.Status.ERROR, "Unknown Command: " + args[0]);
            };
        } catch (Exception e) {
            e.printStackTrace();
            return new ParseResult(ParseResult.Status.ERROR, "Exception occurred while parsing: " + e.getMessage());
        }
    }

    public static class ParseResult {
        public enum Status {
            UNKNOWN,
            SUCCESS,
            ERROR,
        }

        public final Status status;
        public final String message;

        public ParseResult() {
            this(Status.UNKNOWN, "");
        }

        public ParseResult(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public ParseResult onSuccess(Consumer<ParseResult> callback) {
            if (status == Status.SUCCESS) callback.accept(this);
            return this;
        }

        public ParseResult onError(Consumer<ParseResult> callback) {
            if (status == Status.ERROR) callback.accept(this);
            return this;
        }
    }
}
