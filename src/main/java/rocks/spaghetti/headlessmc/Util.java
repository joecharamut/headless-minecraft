package rocks.spaghetti.headlessmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Util {

    public static byte[] getResourceAsBytes(String id) {
        try {
            return MinecraftClient.getInstance().getResourceManager().getResource(new Identifier(id)).getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InetSocketAddress stringToAddress(String address, int defaultPort) {
        String[] parts = address.split(":");
        String host = address;
        int port = defaultPort;

        if (parts.length > 1) {
            host = String.join(":", parts);
            port = Integer.parseInt(parts[parts.length - 1]);
        }

        return new InetSocketAddress(host, port);
    }
}
