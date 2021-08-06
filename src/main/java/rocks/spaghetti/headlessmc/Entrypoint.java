package rocks.spaghetti.headlessmc;

import net.minecraft.client.RunArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import rocks.spaghetti.headlessmc.client.discord.DiscordClient;
import rocks.spaghetti.headlessmc.client.swing.SwingClientMain;

public class Entrypoint {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void onInterceptMain(RunArgs runArgs, String[] argv) {
//        Configurator.initialize(null, "classpath:log4j2_trace.xml");
//        new DiscordClient().run(runArgs);
        new SwingClientMain().run(runArgs);
    }
}
