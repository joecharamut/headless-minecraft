package rocks.spaghetti.headlessmc.client.swing;

import net.minecraft.client.RunArgs;
import rocks.spaghetti.headlessmc.client.LaunchTarget;
import rocks.spaghetti.headlessmc.client.lib.GameTextParser;
import rocks.spaghetti.headlessmc.client.swing.ui.ClientMainWindow;
import rocks.spaghetti.headlessmc.game.GameClient;

import java.awt.*;
import java.net.InetSocketAddress;

public class SwingClientMain implements LaunchTarget {
    private ClientMainWindow mainWindow;

    @Override
    public void run(RunArgs args) {
        GameClient client = new GameClient(args);
        mainWindow = new ClientMainWindow();
        GameTextParser parser = new GameTextParser(client);

        mainWindow.onConsoleInput(text -> parser.parse(text)
                .onSuccess(result -> mainWindow.consolePrintln("< " + result.message, null))
                .onError(result -> mainWindow.consolePrintln("< " + result.message, Color.RED))
        );

        mainWindow.onQueryButton(address -> {
            String[] parts = address.split(":");
            String host = address;
            int port = 25565;

            if (parts.length > 1) {
                host = String.join(":", parts);
                port = Integer.parseInt(parts[parts.length - 1]);
            }

            client.query(new InetSocketAddress(host, port)).ifPresent(mainWindow::setQueryInfo);
        });
    }
}
