package rocks.spaghetti.headlessmc.game;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.resource.ClientBuiltinResourcePackProvider;
import net.minecraft.client.resource.Format3ResourcePack;
import net.minecraft.client.resource.Format4ResourcePack;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.search.IdentifierSearchableContainer;
import net.minecraft.client.search.SearchManager;
import net.minecraft.client.search.SearchableContainer;
import net.minecraft.client.search.TextSearchableContainer;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.tutorial.TutorialManager;
import net.minecraft.client.util.Session;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.listener.ClientQueryPacketListener;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket;
import net.minecraft.network.packet.s2c.query.QueryPongS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.resource.*;
import net.minecraft.server.ServerMetadata;
import net.minecraft.tag.ItemTags;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.ProfilerSystem;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import rocks.spaghetti.headlessmc.ReflectionUtil;
import rocks.spaghetti.headlessmc.event.ClientTickCallback;
import rocks.spaghetti.headlessmc.game.auth.AuthInfo;
import rocks.spaghetti.headlessmc.game.network.LoginNetworkHandler;
import rocks.spaghetti.headlessmc.game.render.GameRendererStub;
import rocks.spaghetti.headlessmc.mixin.MinecraftClientAccessor;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class GameClient extends ReentrantThreadExecutor<Runnable> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Proxy PROXY = Proxy.NO_PROXY;

    private static GameClient instance;

    private final YggdrasilAuthenticationService authService;
    private final MinecraftSessionService sessionService;
    public final Profiler profiler;
    private final ReloadableResourceManager resourceManager;
    private final SoundManager soundManager;
    private final File runDirectory;
    private final SearchManager searchManager;
    private final TutorialManager tutorialManager;
    private final ResourceReloadLogger resourceReloadLogger;
    private final ClientBuiltinResourcePackProvider builtinPackProvider;
    private final ResourcePackManager resourcePackManager;

    private Thread gameThread;
    private int ticks;
    private Session session;
    private ClientConnection connection;
    public GameOptions options;

    public GameInteractionManager interactionManager;
    public ClientPlayerEntity player;
    public ClientWorld world;

    public GameClient(RunArgs args) {
        super("Game Client");

        instance = this;

        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        Bootstrap.logMissing();

        MinecraftClient clientProxy = ReflectionUtil.createProxy(MinecraftClient.class, this);
//        ReflectionUtil.setStaticField(MinecraftClient.class, "instance", clientProxy);
        MinecraftClientAccessor.setInstance(clientProxy);
        ReflectionUtil.setField(clientProxy, "gameRenderer", GameRendererStub.get());

        this.authService = new YggdrasilAuthenticationService(PROXY);
        this.sessionService = authService.createMinecraftSessionService();

        this.profiler = new ProfilerSystem(System::currentTimeMillis, () -> ticks, false);

        this.runDirectory = args.directories.runDir;
        this.options = new GameOptions(MinecraftClient.getInstance(), this.runDirectory);
        ReflectionUtil.setField(clientProxy, "options", this.options);

        this.resourceReloadLogger = new ResourceReloadLogger();
        this.builtinPackProvider = new ClientBuiltinResourcePackProvider(new File(this.runDirectory, "server-resource-packs"), args.directories.getResourceIndex());
        this.resourcePackManager = new ResourcePackManager((name, displayName, alwaysEnabled, packFactory, metadata, initialPosition, source) -> {
            int i = metadata.getPackFormat();
            Supplier<ResourcePack> supplier = packFactory;

            if (i <= 3) {
                supplier = () -> new Format3ResourcePack(packFactory.get(), Format3ResourcePack.NEW_TO_OLD_MAP);
            }

            if (i <= 4) {
                supplier = () -> new Format4ResourcePack(packFactory.get());
            }

            return new ResourcePackProfile(name, displayName, alwaysEnabled, supplier, metadata, ResourceType.CLIENT_RESOURCES, initialPosition, source);
        }, this.builtinPackProvider, new FileResourcePackProvider(args.directories.resourcePackDir, ResourcePackSource.PACK_SOURCE_NONE));
        this.resourceManager = new ReloadableResourceManagerImpl(ResourceType.CLIENT_RESOURCES);
        this.resourcePackManager.scanPacks();

        this.soundManager = new SoundManager(this.resourceManager, this.options);

        this.searchManager = new SearchManager();
        this.resourceManager.registerReloader(this.searchManager);
        initializeSearchableContainers();

        this.tutorialManager = new TutorialManager(MinecraftClient.getInstance(), this.options);

        this.resourceManager.reload(Util.getMainWorkerExecutor(), this, CompletableFuture.completedFuture(Unit.INSTANCE), this.resourcePackManager.createResourcePacks());
    }

    public static GameClient getInstance() {
        return instance;
    }

    private void initializeSearchableContainers() {
        TextSearchableContainer<ItemStack> tooltipSearch = new TextSearchableContainer<>(
                itemStack -> itemStack.getTooltip(null, TooltipContext.Default.NORMAL).stream()
                        .map(text -> Objects.requireNonNull(Formatting.strip(text.getString())).trim())
                        .filter(Predicate.not(String::isEmpty)),
                itemStack -> Stream.of(Registry.ITEM.getId(itemStack.getItem()))
        );

        IdentifierSearchableContainer<ItemStack> tagSearch =
                new IdentifierSearchableContainer<>(itemStack -> ItemTags.getTagGroup().getTagsFor(itemStack.getItem()).stream());

        DefaultedList<ItemStack> items = DefaultedList.of();
        for (Item item : Registry.ITEM) {
            item.appendStacks(ItemGroup.SEARCH, items);
        }
        items.forEach(itemStack -> {
            tooltipSearch.add(itemStack);
            tagSearch.add(itemStack);
        });

        TextSearchableContainer<RecipeResultCollection> recipeResultSearch = new TextSearchableContainer<>(
                results -> results.getAllRecipes().stream()
                        .flatMap(recipe -> recipe.getOutput().getTooltip(null, TooltipContext.Default.NORMAL).stream())
                        .map(text -> Objects.requireNonNull(Formatting.strip(text.getString())).trim())
                        .filter(Predicate.not(String::isEmpty)),
                results -> results.getAllRecipes().stream()
                        .map(recipe -> Registry.ITEM.getId(recipe.getOutput().getItem()))
        );

        this.searchManager.put(SearchManager.ITEM_TOOLTIP, tooltipSearch);
        this.searchManager.put(SearchManager.ITEM_TAG, tagSearch);
        this.searchManager.put(SearchManager.RECIPE_OUTPUT, recipeResultSearch);
    }

    private void gameLoop() {
        long lastTime = System.currentTimeMillis();
        long curTime;
        long delta;

        while (connection.isOpen()) {
            curTime = System.currentTimeMillis();
            delta = curTime - lastTime;

            profiler.startTick();

            connection.tick();
            this.runTasks();

            if (this.player != null) {
                player.tickMovement();
            }

            if (this.world != null) {
                this.world.tickEntities();
            }

            // 20 tps = 50ms/tick
            if (delta >= 50) {

                lastTime = curTime;
                ticks++;
                ClientTickCallback.EVENT.invoker().onTick(this);

            }

            profiler.endTick();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                LOGGER.info("Game Thread Interrupted");
                break;
            }
        }
    }

    public Optional<ServerMetadata> query(InetSocketAddress address) {
        ClientConnection conn;
        try {
            conn = ClientConnection.connect(address, true);
        } catch (Exception e) {
            LOGGER.fatal("Exception Caught: " + e);
            return Optional.empty();
        }

        Thread loop = new Thread(() -> {
            while (conn.isOpen()) {
                conn.tick();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        final ServerMetadata[] result = { null };
        conn.setPacketListener(new ClientQueryPacketListener() {
            @Override
            public void onDisconnected(Text reason) {
                LOGGER.info("Disconnected: {}", reason.getString());
            }

            @Override
            public ClientConnection getConnection() {
                return conn;
            }

            @Override
            public void onResponse(QueryResponseS2CPacket packet) {
                ServerMetadata metadata = packet.getServerMetadata();
                result[0] = metadata;

                LOGGER.info("Server: Minecraft {}, MOTD: {}, Players: {}/{}",
                        metadata.getVersion().getGameVersion(),
                        metadata.getDescription().getString(),
                        metadata.getPlayers().getOnlinePlayerCount(),
                        metadata.getPlayers().getPlayerLimit()
                );
                conn.disconnect(null);
            }

            @Override
            public void onPong(QueryPongS2CPacket packet) {
                LOGGER.info("Pong: {}", System.currentTimeMillis() - packet.getStartTime());
            }
        });
        loop.start();

        conn.send(new HandshakeC2SPacket(address.getHostName(), address.getPort(), NetworkState.STATUS));
        conn.send(new QueryRequestC2SPacket());

        try {
            loop.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return Optional.ofNullable(result[0]);
    }

    public void login(InetSocketAddress address, AuthInfo authInfo, Consumer<Text> statusConsumer) {
        try {
            connection = ClientConnection.connect(address, true);
        } catch (Exception e) {
            LOGGER.catching(e);
            statusConsumer.accept(new TranslatableText("multiplayer.status.cannot_connect"));
            statusConsumer.accept(new LiteralText(e.getMessage()));
            return;
        }

        gameThread = new Thread(this::gameLoop);
        connection.setPacketListener(new LoginNetworkHandler(this, statusConsumer));
        gameThread.start();

        session = new Session(authInfo.getUsername(), UUIDTypeAdapter.fromUUID(authInfo.getUuid()), authInfo.getAccessToken(), authInfo.getAccountType());
        connection.send(new HandshakeC2SPacket(address.getHostName(), address.getPort(), NetworkState.LOGIN));
        connection.send(new LoginHelloC2SPacket(session.getProfile()));

        try {
            gameThread.join();
        } catch (InterruptedException e) {
            LOGGER.catching(e);
        }

        if (connection.isOpen()) {
            connection.disconnect(null);
        }

        Text reason = connection.getDisconnectReason();
        if (reason != null) {
            LOGGER.info("Disconnected (Reason: {})", reason.getString());
            statusConsumer.accept(reason);
        } else {
            LOGGER.info("Disconnected (Reason: null)");
            statusConsumer.accept(new TranslatableText("multiplayer.disconnect.generic"));
        }
    }

    public void disconnect() {
        this.connection.disconnect(null);
    }

    public void sendChatMessage(String message) {
        this.connection.send(new ChatMessageC2SPacket(message));
    }

    public void joinWorld(ClientWorld world) {
        this.world = world;
    }



    public ClientConnection getConnection() {
        return connection;
    }

    public YggdrasilAuthenticationService getAuthService() {
        return authService;
    }

    public MinecraftSessionService getSessionService() {
        return sessionService;
    }

    public Session getSession() {
        return session;
    }

    public ClientWorld getWorld() {
        return world;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public <T> SearchableContainer<T> getSearchableContainer(SearchManager.Key<T> key) {
        return this.searchManager.get(key);
    }

    /**
     * {@link MinecraftClient#is64Bit()}
     */
    @SuppressWarnings("unused")
    public boolean is64Bit() {
        String[] keys = new String[]{"sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch"};

        for (String prop : keys) {
            String archString = System.getProperty(prop);
            if (archString != null && archString.contains("64")) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@link MinecraftClient#getNetworkHandler()}
     */
    @SuppressWarnings("unused")
    @Nullable
    public ClientPlayNetworkHandler getNetworkHandler() {
        return this.player == null ? null : this.player.networkHandler;
    }

    /**
     * {@link MinecraftClient#getTutorialManager()}
     */
    @SuppressWarnings("unused")
    public TutorialManager getTutorialManager() {
        return this.tutorialManager;
    }

    /**
     * {@link MinecraftClient#getCameraEntity()}
     */
    @SuppressWarnings("unused")
    @Nullable
    public Entity getCameraEntity() {
        return this.player;
    }

    @Override
    protected Runnable createTask(Runnable runnable) {
        return runnable;
    }

    @Override
    protected boolean canExecute(Runnable task) {
        return true;
    }

    @Override
    protected Thread getThread() {
        return gameThread;
    }

    /**
     * {@link MinecraftClient#getResourceManager()}
     */
    public ResourceManager getResourceManager() {
        return resourceManager;
    }
}
