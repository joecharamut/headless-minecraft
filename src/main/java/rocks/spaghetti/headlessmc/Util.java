package rocks.spaghetti.headlessmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.IOException;

public class Util {

    public static byte[] getResourceAsBytes(String id) {
        try {
            return MinecraftClient.getInstance().getResourceManager().getResource(new Identifier(id)).getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
