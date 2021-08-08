package rocks.spaghetti.headlessmc.client.swing;

import net.minecraft.client.RunArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rocks.spaghetti.headlessmc.util.Util;
import rocks.spaghetti.headlessmc.client.LaunchTarget;
import rocks.spaghetti.headlessmc.client.lib.GameTextParser;
import rocks.spaghetti.headlessmc.client.swing.ui.ClientMainWindow;
import rocks.spaghetti.headlessmc.event.ClientChatCallback;
import rocks.spaghetti.headlessmc.event.ClientTickCallback;
import rocks.spaghetti.headlessmc.game.GameClient;
import rocks.spaghetti.headlessmc.game.auth.AuthInfo;

import java.awt.*;
import java.net.InetSocketAddress;

public class SwingClientMain implements LaunchTarget {
    private static final Logger LOGGER = LogManager.getLogger();

    private ClientMainWindow mainWindow;
    private Thread gameThread;
    private int ticks;

    public SwingClientMain() {
        System.setProperty("java.awt.headless", "false");
    }

    @Override
    public void run(RunArgs args) {
        GameClient client = new GameClient(args);
        mainWindow = new ClientMainWindow();
        GameTextParser parser = new GameTextParser(client);

        mainWindow.onConsoleInput(text -> parser.parse(text)
                .onSuccess(result -> mainWindow.consolePrintln("< " + result.message, null))
                .onError(result -> mainWindow.consolePrintln("< " + result.message, Color.RED))
        );

        mainWindow.onQueryButton(address -> client.query(Util.stringToAddress(address, 25565)).ifPresent(mainWindow::setQueryInfo));

        mainWindow.onConnectButton(address -> {
            mainWindow.setTab("Console");

            InetSocketAddress server = Util.stringToAddress(address, 25565);

            ClientChatCallback.EVENT.register((location, message, sender) -> {
                LOGGER.info("[CHAT] {}", message.getString());
                mainWindow.consolePrintln(String.format("> [CHAT] %s", message.getString()), null);
            });

            ClientTickCallback.EVENT.register((game -> {
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

            gameThread = new Thread(() -> {
                client.login(server, AuthInfo.offlineUser(client, "Herobrine"), text -> {
                    LOGGER.info(text.getString());
                    mainWindow.consolePrintln(text.getString(), null);
                });
            });
            gameThread.start();
        });

        mainWindow.onClose(() -> {
            if (gameThread != null) {
                client.disconnect();
                try {
                    gameThread.join();
                } catch (InterruptedException e) {
                    LOGGER.catching(e);
                }
            }
        });
    }
}
