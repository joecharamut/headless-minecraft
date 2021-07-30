package rocks.spaghetti.headlessmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.properties.PropertyMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.client.RunArgs;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.Session;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import rocks.spaghetti.headlessmc.client.discord.DiscordClient;

import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.Arrays;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger();

    private static RunArgs parseArgs(String[] args) {
        LOGGER.info("Launching game with args: {}", Arrays.asList(args));
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        OptionSpec<String> serverHostSpec = parser.accepts("server").withRequiredArg();
        OptionSpec<Integer> serverPortSpec = parser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(25565);

        OptionSpec<File> gameDirSpec = parser.accepts("gameDir").withRequiredArg().ofType(File.class).defaultsTo(new File("."));
        OptionSpec<File> assetsDirSpec = parser.accepts("assetsDir").withRequiredArg().ofType(File.class);
        OptionSpec<File> resourcePackDirSpec = parser.accepts("resourcePackDir").withRequiredArg().ofType(File.class);
        OptionSpec<String> assetIndexSpec = parser.accepts("assetIndex").withRequiredArg();

        OptionSpec<String> proxyHostSpec = parser.accepts("proxyHost").withRequiredArg();
        OptionSpec<Integer> proxyPortSpec = parser.accepts("proxyPort").withRequiredArg().ofType(Integer.class).defaultsTo(8080);
        OptionSpec<String> proxyUserSpec = parser.accepts("proxyUser").withRequiredArg();
        OptionSpec<String> proxyPassSpec = parser.accepts("proxyPass").withRequiredArg();

        OptionSpec<String> usernameSpec = parser.accepts("username").withRequiredArg().defaultsTo("Player" + Util.getMeasuringTimeMs() % 1000L);
        OptionSpec<String> uuidSpec = parser.accepts("uuid").withRequiredArg();
        OptionSpec<String> accessTokenSpec = parser.accepts("accessToken").withRequiredArg().required();

        OptionSpec<String> versionSpec = parser.accepts("version").withRequiredArg().required();

        OptionSpec<String> userPropertiesSpec = parser.accepts("userProperties").withRequiredArg().defaultsTo("{}");
        OptionSpec<String> profilePropertiesSpec = parser.accepts("profileProperties").withRequiredArg().defaultsTo("{}");
        OptionSpec<String> userTypeSpec = parser.accepts("userType").withRequiredArg().defaultsTo("legacy");
        OptionSpec<String> versionTypeSpec = parser.accepts("versionType").withRequiredArg().defaultsTo("release");

        OptionSet opts = parser.parse(args);

        Proxy proxy = Proxy.NO_PROXY;
        String hostString = proxyHostSpec.value(opts);
        Integer portNum = proxyPortSpec.value(opts);
        if (hostString != null) {
            try {
                proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostString, portNum));
            } catch (Exception e) {
                // no proxy on fail
            }
        }

        String userString = proxyUserSpec.value(opts);
        String passString = proxyPassSpec.value(opts);
        if (!proxy.equals(Proxy.NO_PROXY) && userString != null && !userString.isEmpty() && passString != null && !passString.isEmpty()) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(userString, passString.toCharArray());
                }
            });
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create();
        PropertyMap userPropertiesMap = JsonHelper.deserialize(gson, userPropertiesSpec.value(opts), PropertyMap.class);
        PropertyMap profilePropertiesMap = JsonHelper.deserialize(gson, profilePropertiesSpec.value(opts), PropertyMap.class);

        String uuidString = opts.has(uuidSpec) ? uuidSpec.value(opts) : PlayerEntity.getOfflinePlayerUuid(usernameSpec.value(opts)).toString();
        Session session = new Session(usernameSpec.value(opts), uuidString, accessTokenSpec.value(opts), userTypeSpec.value(opts));

        File gameDir = gameDirSpec.value(opts);
        File resourcePackDir = opts.has(resourcePackDirSpec) ? resourcePackDirSpec.value(opts) : new File(gameDir, "resourcepacks/");
        File assetsDir = opts.has(assetsDirSpec) ? assetsDirSpec.value(opts) : new File(gameDir, "assets/");
        String assetIndex = opts.has(assetIndexSpec) ? assetIndexSpec.value(opts) : null;

        return new RunArgs(
                new RunArgs.Network(session, userPropertiesMap, profilePropertiesMap, proxy),
                new WindowSettings(0, 0, null, null, false),
                new RunArgs.Directories(gameDir, resourcePackDir, assetsDir, assetIndex),
                new RunArgs.Game(false, versionSpec.value(opts), versionTypeSpec.value(opts), false, false),
                new RunArgs.AutoConnect(serverHostSpec.value(opts), serverPortSpec.value(opts))
        );
    }

    public static void main(String[] args) {
        Configurator.initialize(null, "classpath:log4j2_trace.xml");
        RunArgs runArgs = parseArgs(args);

        new DiscordClient().run(runArgs);
    }
}
