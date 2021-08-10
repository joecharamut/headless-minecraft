package rocks.spaghetti.headlessmc.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import rocks.spaghetti.headlessmc.game.GameClient;

public interface ClientTickCallback {
    Event<ClientTickCallback> START_TICK = EventFactory.createArrayBacked(ClientTickCallback.class, (listeners) -> (client) -> {
        for (ClientTickCallback listener : listeners) {
            listener.onTick(client);
        }
    });

    Event<ClientTickCallback> END_TICK = EventFactory.createArrayBacked(ClientTickCallback.class, (listeners) -> (client) -> {
        for (ClientTickCallback listener : listeners) {
            listener.onTick(client);
        }
    });

    void onTick(GameClient client);
}
