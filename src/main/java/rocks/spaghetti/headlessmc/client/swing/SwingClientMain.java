package rocks.spaghetti.headlessmc.client.swing;

import net.minecraft.client.RunArgs;
import rocks.spaghetti.headlessmc.client.LaunchTarget;
import rocks.spaghetti.headlessmc.client.lib.GameTextParser;
import rocks.spaghetti.headlessmc.client.swing.ui.ClientMainWindow;
import rocks.spaghetti.headlessmc.game.GameClient;

import java.awt.*;

public class SwingClientMain implements LaunchTarget {
    private ClientMainWindow mainWindow;

    @Override
    public void run(RunArgs args) {
        mainWindow = new ClientMainWindow();

        GameClient client = new GameClient(args);
        GameTextParser parser = new GameTextParser(client);

        mainWindow.onConsoleInput(text -> parser.parse(text)
                .onSuccess(result -> mainWindow.consolePrintln("< " + result.message, null))
                .onError(result -> mainWindow.consolePrintln("< " + result.message, Color.RED))
        );
    }
}
