package rocks.spaghetti.headlessmc.mixin;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerListEntry.class)
public interface PlayerListEntryInvoker {
    @Invoker("setGameMode")
    void setGameMode(GameMode gameMode);

    @Invoker("setLatency")
    void setLatency(int latency);
}
