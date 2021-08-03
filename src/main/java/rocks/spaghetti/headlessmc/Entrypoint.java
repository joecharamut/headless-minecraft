package rocks.spaghetti.headlessmc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.client.RunArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import rocks.spaghetti.headlessmc.client.discord.DiscordClient;
import rocks.spaghetti.headlessmc.client.swing.SwingClientMain;

import java.util.Arrays;

public class Entrypoint implements ClientModInitializer, DedicatedServerModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void clientMain(RunArgs runArgs, String[] argv) {
        Configurator.initialize(null, "classpath:log4j2_trace.xml");

        if (Arrays.asList(argv).contains("nogui")) {
            LOGGER.info("nogui specified, forcing java.awt.headless=true");
            System.setProperty("java.awt.headless", "true");
        } else {
            System.setProperty("java.awt.headless", "false");
        }

//        new DiscordClient().run(runArgs);
        new SwingClientMain().run(runArgs);
    }

    @Override
    public void onInitializeClient() {
        throw new IllegalStateException("Should never reach here!");
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("hi dedicated server :3");
    }
}
