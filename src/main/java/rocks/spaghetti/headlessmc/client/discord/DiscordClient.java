package rocks.spaghetti.headlessmc.client.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.client.RunArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import rocks.spaghetti.headlessmc.client.LaunchTarget;
import rocks.spaghetti.headlessmc.client.lib.GameTextParser;
import rocks.spaghetti.headlessmc.event.ClientChatCallback;
import rocks.spaghetti.headlessmc.event.ClientTickCallback;
import rocks.spaghetti.headlessmc.game.GameClient;
import rocks.spaghetti.headlessmc.game.auth.AuthInfo;

import javax.security.auth.login.LoginException;
import java.net.InetSocketAddress;

public class DiscordClient implements LaunchTarget {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Object discordLock = new Object();
    private boolean discordExited = false;

    private GameClient client;
    private MessageChannel gameChannel;
    private Thread gameThread;
    private GameTextParser parser;

    @Override
    public void run(RunArgs args) {
        this.client = new GameClient(args);
        this.parser = new GameTextParser(client);

        String token = System.getenv("discord.token");
        try {
            JDA discord = JDABuilder
                    .createDefault(token)
                    .addEventListeners((EventListener) event -> {
                        if (event instanceof ReadyEvent) {
                            LOGGER.info("Discord bot ready");
                        }
                    })
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                            handleMessage(event.getMessage());
                        }
                    })
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onShutdown(@NotNull ShutdownEvent event) {
                            synchronized (discordLock) {
                                discordExited = true;
                                discordLock.notifyAll();
                            }
                        }
                    })
                    .build();
            discord.awaitReady();
        } catch (LoginException | InterruptedException e) {
            LOGGER.catching(e);
        }

        synchronized (discordLock) {
            while (!discordExited) {
                try {
                    discordLock.wait();
                } catch (InterruptedException e) {
                    LOGGER.catching(e);
                }
            }
        }
    }

    private void handleMessage(Message message) {
        String content = message.getContentRaw();
        if (!content.startsWith("%")) return;
        content = content.substring(1);
        String[] args = content.split(" ");

        switch (args[0]) {
            case "ping" -> message.getChannel().sendMessage("Pong!").queue();

            case "info" -> {
                String reply = String.format("Connected to: %s, avg packets sent: %.2f, avg packets received: %.2f",
                        client.getConnection().getAddress(),
                        client.getConnection().getAveragePacketsSent(),
                        client.getConnection().getAveragePacketsReceived()
                );

                message.getChannel().sendMessage(reply).queue();
            }

            case "connect" -> {
                if (gameThread == null) {
                    gameChannel = message.getChannel();
                    gameChannel.sendMessage("Connecting to server...").queue();
                    gameThread = new Thread(this::runGame);
                    gameThread.start();
                } else {
                    message.getChannel().sendMessage("Game already in progress").queue();
                }
            }

            case "disconnect" -> {
                if (gameThread != null) {
                    client.disconnect();
                    try {
                        gameThread.join();
                    } catch (InterruptedException e) {
                        LOGGER.catching(e);
                    }
                    gameThread = null;
                } else {
                    message.getChannel().sendMessage("No game in progress").queue();
                }
            }

            case "mc" -> parser.parse(content.substring(2))
                    .onSuccess(result -> gameChannel.sendMessage(result.message).queue())
                    .onError(result -> gameChannel.sendMessage(result.message).queue());
        }
    }

    private int ticks;
    private void runGame() {
        InetSocketAddress server = new InetSocketAddress("localhost", 25565);

        ClientChatCallback.EVENT.register((location, message, sender) -> {
            LOGGER.info("[CHAT] {}", message.getString());
            gameChannel.sendMessage("[CHAT] " + message.getString()).queue();
        });

        ClientTickCallback.START_TICK.register((game -> {
            switch (ticks++) {
                case 20 -> {
                    client.sendChatMessage("minecraft zombie says bruh");
                    LOGGER.info("Sent Message");
                }
                case 40 -> {
//                client.disconnect();
                    LOGGER.info("2 seconds");
                }
                default -> {
                    // do nothing
                }
            }
        }));

        client.login(server, AuthInfo.offlineUser(client, "Herobrine"), text -> gameChannel.sendMessage(text.getString()).queue());
    }
}
