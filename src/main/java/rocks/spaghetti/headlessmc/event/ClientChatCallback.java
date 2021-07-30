package rocks.spaghetti.headlessmc.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;

import java.util.UUID;

public interface ClientChatCallback {
    Event<ClientChatCallback> EVENT = EventFactory.createArrayBacked(ClientChatCallback.class, (listeners) -> (location, message, sender) -> {
        for (ClientChatCallback listener : listeners) {
            listener.onMessage(location, message, sender);
        }
    });

    void onMessage(MessageType location, Text message, UUID sender);
}
