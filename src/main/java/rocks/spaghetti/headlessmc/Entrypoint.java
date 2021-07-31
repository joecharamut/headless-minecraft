package rocks.spaghetti.headlessmc;

import net.minecraft.client.RunArgs;
import org.apache.logging.log4j.core.config.Configurator;
import rocks.spaghetti.headlessmc.client.discord.DiscordClient;

public class Entrypoint {
    public static void clientMain(RunArgs args) {
        Configurator.initialize(null, "classpath:log4j2_trace.xml");

        new DiscordClient().run(args);
    }
}
