package rocks.spaghetti.headlessmc.mixin;

import com.google.gson.Gson;
import com.mojang.authlib.properties.PropertyMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.client.RunArgs;
import net.minecraft.client.util.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import rocks.spaghetti.headlessmc.Entrypoint;

import java.io.File;
import java.net.Proxy;
import java.util.OptionalInt;

@Mixin(net.minecraft.client.main.Main.class)
@SuppressWarnings("java:S1118")
public abstract class MinecraftClientMainMixin {
    @Inject(
            method = "main",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;setUncaughtExceptionHandler(Ljava/lang/Thread$UncaughtExceptionHandler;)V"),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    @SuppressWarnings({"rawtypes", "OptionalUsedAsFieldOrParameterType"})
    private static void redirectMain(String[] args,
                                     CallbackInfo ci,
                                     OptionParser optionParser,
                                     OptionSpec optionSpec,
                                     OptionSpec optionSpec2,
                                     OptionSpec optionSpec3,
                                     OptionSpec optionSpec4,
                                     OptionSpec optionSpec5,
                                     OptionSpec optionSpec6,
                                     OptionSpec optionSpec7,
                                     OptionSpec optionSpec8,
                                     OptionSpec optionSpec9,
                                     OptionSpec optionSpec10,
                                     OptionSpec optionSpec11,
                                     OptionSpec optionSpec12,
                                     OptionSpec optionSpec13,
                                     OptionSpec optionSpec14,
                                     OptionSpec optionSpec15,
                                     OptionSpec optionSpec16,
                                     OptionSpec optionSpec17,
                                     OptionSpec optionSpec18,
                                     OptionSpec optionSpec19,
                                     OptionSpec optionSpec20,
                                     OptionSpec optionSpec21,
                                     OptionSpec optionSpec22,
                                     OptionSpec optionSpec23,
                                     OptionSet optionSet,
                                     String string,
                                     Proxy proxy,
                                     int i,
                                     int j,
                                     OptionalInt optionalInt,
                                     OptionalInt optionalInt2,
                                     boolean bl,
                                     boolean bl2,
                                     boolean bl3,
                                     boolean bl4,
                                     String string4,
                                     Gson gson,
                                     PropertyMap propertyMap,
                                     PropertyMap propertyMap2,
                                     String string5,
                                     File file,
                                     File file2,
                                     File file3,
                                     String string6,
                                     String string7,
                                     String string8,
                                     Integer integer,
                                     Session session,
                                     RunArgs runArgs) {
        Entrypoint.clientMain(runArgs, args);
        ci.cancel();
    }
}
