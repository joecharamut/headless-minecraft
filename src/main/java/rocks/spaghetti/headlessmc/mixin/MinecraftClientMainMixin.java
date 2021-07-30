package rocks.spaghetti.headlessmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.main.Main.class)
public abstract class MinecraftClientMainMixin {
    private MinecraftClientMainMixin() { }

    @Inject(method = "main", at = @At(value = "HEAD"))
    private static void redirectMain(String[] args, CallbackInfo ci) {
        rocks.spaghetti.headlessmc.Main.main(args);
        System.exit(0);
    }
}
