package rocks.spaghetti.headlessmc.game.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.entity.*;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.search.SearchManager;
import net.minecraft.client.search.SearchableContainer;
import net.minecraft.client.sound.AggressiveBeeSoundInstance;
import net.minecraft.client.sound.GuardianAttackSoundInstance;
import net.minecraft.client.sound.PassiveBeeSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.StatHandler;
import net.minecraft.tag.RequiredTagListRegistry;
import net.minecraft.tag.TagManager;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import rocks.spaghetti.headlessmc.util.ReflectionUtil;
import rocks.spaghetti.headlessmc.event.ClientChatCallback;
import rocks.spaghetti.headlessmc.game.GameClient;
import rocks.spaghetti.headlessmc.game.GameInteractionManager;
import rocks.spaghetti.headlessmc.game.SettableInput;
import rocks.spaghetti.headlessmc.game.render.WorldRendererStub;

import java.util.*;
import java.util.function.IntConsumer;

/**
 * @see net.minecraft.client.network.ClientPlayNetworkHandler
 */
public class PlayNetworkHandler implements ClientPlayPacketListener {
    private static final Logger LOGGER = LogManager.getLogger();

    private final GameClient client;
    private final ClientConnection connection;
    private final RecipeManager recipeManager;
    private final GameProfile profile;
    private final Map<UUID, PlayerListEntry> playerListEntries;

    private TagManager tagManager;
    private ClientWorld world;
    private int chunkLoadDistance;
    private Set<RegistryKey<World>> worldKeys;
    private ClientWorld.Properties worldProperties;
    private DynamicRegistryManager registryManager;
    private CommandDispatcher<CommandSource> commandDispatcher;

    public PlayNetworkHandler(GameClient client, ClientConnection connection, GameProfile profile) {
        this.tagManager = TagManager.EMPTY;
        this.registryManager = DynamicRegistryManager.create();
        this.recipeManager = new RecipeManager();

        this.client = client;
        this.connection = connection;
        this.profile = profile;
        this.playerListEntries = Maps.newHashMap();
    }

    public void sendPacket(Packet<?> packet) {
        client.getConnection().send(packet);
    }

    @Override
    public void onEntitySpawn(EntitySpawnS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        EntityType<?> entityType = packet.getEntityTypeId();
        Entity entity = entityType.create(this.world);
        if (entity != null) {
            entity.onSpawnPacket(packet);
            int i = packet.getId();
            this.world.addEntity(i, entity);
        }
    }

    @Override
    public void onExperienceOrbSpawn(ExperienceOrbSpawnS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onVibration(VibrationS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onMobSpawn(MobSpawnS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        LivingEntity entity = (LivingEntity) EntityType.createInstanceFromId(packet.getEntityTypeId(), this.world);
        if (entity != null) {
            entity.readFromPacket(packet);
            this.world.addEntity(packet.getId(), entity);

            if (entity instanceof BeeEntity) {
                if (((BeeEntity) entity).hasAngerTime()) {
                    this.client.getSoundManager().playNextTick(new AggressiveBeeSoundInstance((BeeEntity)entity));
                } else {
                    this.client.getSoundManager().playNextTick(new PassiveBeeSoundInstance((BeeEntity)entity));
                }
            }
        } else {
            LOGGER.warn("Skipping Entity with id {}", packet.getEntityTypeId());
        }
    }

    @Override
    public void onScoreboardObjectiveUpdate(ScoreboardObjectiveUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPaintingSpawn(PaintingSpawnS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPlayerSpawn(PlayerSpawnS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEntityAnimation(EntityAnimationS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onStatistics(StatisticsS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onUnlockRecipes(UnlockRecipesS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        ClientRecipeBook clientRecipeBook;
        clientRecipeBook = this.client.player.getRecipeBook();
        clientRecipeBook.setOptions(packet.getOptions());

        switch (packet.getAction()) {
            case REMOVE -> packet.getRecipeIdsToChange().forEach(id -> recipeManager.get(id).ifPresent(clientRecipeBook::remove));
            case INIT -> {
                packet.getRecipeIdsToChange().forEach(id -> recipeManager.get(id).ifPresent(clientRecipeBook::add));
                packet.getRecipeIdsToInit().forEach(id -> recipeManager.get(id).ifPresent(clientRecipeBook::display));
            }
            case ADD -> packet.getRecipeIdsToChange().forEach(id -> this.recipeManager.get(id).ifPresent(recipe -> {
                clientRecipeBook.add(recipe);
                clientRecipeBook.display(recipe);
//                RecipeToast.show(this.client.getToastManager(), recipe);
            }));
        }

        clientRecipeBook.getOrderedResults().forEach(results -> results.initialize(clientRecipeBook));
//        if (this.client.currentScreen instanceof RecipeBookProvider) {
//            ((RecipeBookProvider)this.client.currentScreen).refreshRecipeBook();
//        }
    }

    @Override
    public void onBlockDestroyProgress(BlockBreakingProgressS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSignEditorOpen(SignEditorOpenS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onBlockEntityUpdate(BlockEntityUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        BlockPos blockPos = packet.getPos();
        BlockEntity blockEntity = this.client.world.getBlockEntity(blockPos);
        int type = packet.getBlockEntityType();

        if (type == BlockEntityUpdateS2CPacket.MOB_SPAWNER && blockEntity instanceof MobSpawnerBlockEntity ||
                type == BlockEntityUpdateS2CPacket.COMMAND_BLOCK && blockEntity instanceof CommandBlockBlockEntity ||
                type == BlockEntityUpdateS2CPacket.BEACON && blockEntity instanceof BeaconBlockEntity ||
                type == BlockEntityUpdateS2CPacket.SKULL && blockEntity instanceof SkullBlockEntity ||
                type == BlockEntityUpdateS2CPacket.BANNER && blockEntity instanceof BannerBlockEntity ||
                type == BlockEntityUpdateS2CPacket.STRUCTURE && blockEntity instanceof StructureBlockBlockEntity ||
                type == BlockEntityUpdateS2CPacket.END_GATEWAY && blockEntity instanceof EndGatewayBlockEntity ||
                type == BlockEntityUpdateS2CPacket.SIGN && blockEntity instanceof SignBlockEntity ||
                type == BlockEntityUpdateS2CPacket.BED && blockEntity instanceof BedBlockEntity ||
                type == BlockEntityUpdateS2CPacket.CONDUIT && blockEntity instanceof ConduitBlockEntity ||
                type == BlockEntityUpdateS2CPacket.JIGSAW && blockEntity instanceof JigsawBlockEntity ||
                type == BlockEntityUpdateS2CPacket.CAMPFIRE && blockEntity instanceof CampfireBlockEntity ||
                type == BlockEntityUpdateS2CPacket.BEEHIVE && blockEntity instanceof BeehiveBlockEntity) {
            blockEntity.readNbt(packet.getNbt());
        }
    }

    @Override
    public void onBlockEvent(BlockEventS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onBlockUpdate(BlockUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.world.setBlockStateWithoutNeighborUpdates(packet.getPos(), packet.getState());
    }

    @Override
    public void onGameMessage(GameMessageS2CPacket packet) {
        ClientChatCallback.EVENT.invoker().onMessage(packet.getLocation(), packet.getMessage(), packet.getSender());
    }

    @Override
    public void onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        int flags = Block.NOTIFY_ALL | Block.FORCE_STATE | (packet.shouldSkipLightingUpdates() ? Block.SKIP_LIGHTING_UPDATES : 0);
        packet.visitUpdates((blockPos, blockState) -> this.world.setBlockState(blockPos, blockState, flags));
    }

    @Override
    public void onMapUpdate(MapUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCloseScreen(CloseScreenS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onInventory(InventoryS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        PlayerEntity playerEntity = this.client.player;

        if (packet.getSyncId() == 0) {
            playerEntity.playerScreenHandler.updateSlotStacks(packet.getRevision(), packet.getContents(), packet.getCursorStack());
        } else if (packet.getSyncId() == playerEntity.currentScreenHandler.syncId) {
            playerEntity.currentScreenHandler.updateSlotStacks(packet.getRevision(), packet.getContents(), packet.getCursorStack());
        }
    }

    @Override
    public void onOpenHorseScreen(OpenHorseScreenS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onScreenHandlerPropertyUpdate(ScreenHandlerPropertyUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCustomPayload(CustomPayloadS2CPacket packet) {
        LOGGER.debug("CustomPayloadS2CPacket{Channel: {}, Data: [{}]}", packet.getChannel(), ByteBufUtil.hexDump(packet.getData()));
    }

    @Override
    public void onDisconnect(DisconnectS2CPacket packet) {
        this.connection.disconnect(packet.getReason());
    }

    @Override
    public void onEntityStatus(EntityStatusS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = packet.getEntity(this.world);

        if (entity != null) {
            if (packet.getStatus() == EntityStatuses.PLAY_GUARDIAN_ATTACK_SOUND) {
                this.client.getSoundManager().play(new GuardianAttackSoundInstance((GuardianEntity) entity));
            } else if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
//                this.client.particleManager.addEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
                this.world.playSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_TOTEM_USE, entity.getSoundCategory(), 1.0F, 1.0F, false);
//                if (entity == this.client.player) {
//                    this.client.gameRenderer.showFloatingItem(getActiveTotemOfUndying(this.client.player));
//                }
            } else {
                entity.handleStatus(packet.getStatus());
            }
        }
    }

    @Override
    public void onEntityAttach(EntityAttachS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEntityPassengersSet(EntityPassengersSetS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onExplosion(ExplosionS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onGameStateChange(GameStateChangeS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onKeepAlive(KeepAliveS2CPacket packet) {
        this.sendPacket(new KeepAliveC2SPacket(packet.getId()));
    }

    @Override
    public void onChunkData(ChunkDataS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        int x = packet.getX();
        int z = packet.getZ();

        BiomeArray biomeArray = new BiomeArray(this.registryManager.get(Registry.BIOME_KEY), this.world, packet.getBiomeArray());
        WorldChunk worldChunk = this.world.getChunkManager().loadChunkFromPacket(x, z, biomeArray, packet.getReadBuffer(), packet.getHeightmaps(), packet.getVerticalStripBitmask());

        if (worldChunk != null) {
            for (NbtCompound tag : packet.getBlockEntityTagList()) {
                BlockPos blockPos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                BlockEntity blockEntity = worldChunk.getBlockEntity(blockPos, WorldChunk.CreationType.IMMEDIATE);

                if (blockEntity != null) {
                    blockEntity.readNbt(tag);
                }
            }
        }
    }

    @Override
    public void onUnloadChunk(UnloadChunkS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldEvent(WorldEventS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        if (packet.isGlobal()) {
            this.client.world.syncGlobalEvent(packet.getEventId(), packet.getPos(), packet.getData());
        } else {
            this.client.world.syncWorldEvent(packet.getEventId(), packet.getPos(), packet.getData());
        }
    }

    @Override
    public void onGameJoin(GameJoinS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.client.interactionManager = new GameInteractionManager(this.client, this);
        if (!this.connection.isLocal()) {
            RequiredTagListRegistry.clearAllTags();
        }

        List<RegistryKey<World>> list = Lists.newArrayList(packet.getDimensionIds());
        Collections.shuffle(list);
        this.worldKeys = Sets.newLinkedHashSet(list);
        this.registryManager = packet.getRegistryManager();
        RegistryKey<World> registryKey = packet.getDimensionId();
        DimensionType dimensionType = packet.getDimensionType();
        this.chunkLoadDistance = packet.getViewDistance();

        this.worldProperties = new ClientWorld.Properties(Difficulty.NORMAL, packet.isHardcore(), packet.isFlatWorld());
        this.world = new ClientWorld(
                ReflectionUtil.createProxy(ClientPlayNetworkHandler.class, this),
                this.worldProperties,
                registryKey,
                dimensionType,
                this.chunkLoadDistance,
                () -> client.profiler,
                WorldRendererStub.get(),
                packet.isDebugWorld(),
                packet.getSha256Seed()
        );
        this.client.joinWorld(this.world);

        if (this.client.player == null) {
            this.client.player = this.client.interactionManager.createPlayer(this.world, new StatHandler(), new ClientRecipeBook());
            this.client.player.setYaw(-180.0F);
//            if (this.client.getServer() != null) {
//                this.client.getServer().setLocalPlayerUuid(this.client.player.getUuid());
//            }
        }

//        this.client.debugRenderer.reset();
        this.client.player.init();
        int id = packet.getEntityId();
        this.client.player.setId(id);
        this.world.addEntity(id, this.client.player);
        this.client.player.input = new SettableInput(this.client.player);
        this.client.interactionManager.copyAbilities(this.client.player);
//        this.client.cameraEntity = this.client.player;
//        this.client.openScreen(new DownloadingTerrainScreen());
        this.client.player.setReducedDebugInfo(packet.hasReducedDebugInfo());
        this.client.player.setShowsDeathScreen(packet.showsDeathScreen());
        this.client.interactionManager.setGameModes(packet.getGameMode(), packet.getPreviousGameMode());
        this.client.options.sendClientSettings();
        this.connection.send(new CustomPayloadC2SPacket(CustomPayloadC2SPacket.BRAND, (new PacketByteBuf(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
//        this.client.getGame().onStartGameSession();
    }

    @Override
    public void onEntityUpdate(EntityS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = packet.getEntity(this.world);

        if (entity != null && !entity.isLogicalSideForUpdatingMovement()) {
            if (packet.isPositionChanged()) {
                Vec3d vec3d = packet.calculateDeltaPosition(entity.getTrackedPosition());
                entity.updateTrackedPosition(vec3d);
                float yaw = packet.hasRotation() ? (float)(packet.getYaw() * 360) / 256.0F : entity.getYaw();
                float pitch = packet.hasRotation() ? (float)(packet.getPitch() * 360) / 256.0F : entity.getPitch();
                entity.updateTrackedPositionAndAngles(vec3d.getX(), vec3d.getY(), vec3d.getZ(), yaw, pitch, 3, false);
            } else if (packet.hasRotation()) {
                float yaw = (float)(packet.getYaw() * 360) / 256.0F;
                float pitch = (float)(packet.getPitch() * 360) / 256.0F;
                entity.updateTrackedPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), yaw, pitch, 3, false);
            }

            entity.setOnGround(packet.isOnGround());
        }
    }

    @Override
    public void onPlayerPositionLook(PlayerPositionLookS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        PlayerEntity playerEntity = this.client.player;
        if (packet.shouldDismount()) {
            playerEntity.dismountVehicle();
        }

        Vec3d velocity = playerEntity.getVelocity();
        double x;
        double dx;
        if (packet.getFlags().contains(PlayerPositionLookS2CPacket.Flag.X)) {
            x = velocity.getX();
            dx = playerEntity.getX() + packet.getX();
            playerEntity.lastRenderX += packet.getX();
        } else {
            x = 0.0D;
            dx = packet.getX();
            playerEntity.lastRenderX = dx;
        }

        double y;
        double dy;
        if (packet.getFlags().contains(PlayerPositionLookS2CPacket.Flag.Y)) {
            y = velocity.getY();
            dy = playerEntity.getY() + packet.getY();
            playerEntity.lastRenderY += packet.getY();
        } else {
            y = 0.0D;
            dy = packet.getY();
            playerEntity.lastRenderY = dy;
        }

        double z;
        double dz;
        if (packet.getFlags().contains(PlayerPositionLookS2CPacket.Flag.Z)) {
            z = velocity.getZ();
            dz = playerEntity.getZ() + packet.getZ();
            playerEntity.lastRenderZ += packet.getZ();
        } else {
            z = 0.0D;
            dz = packet.getZ();
            playerEntity.lastRenderZ = dz;
        }

        playerEntity.setPos(dx, dy, dz);
        playerEntity.prevX = dx;
        playerEntity.prevY = dy;
        playerEntity.prevZ = dz;
        playerEntity.setVelocity(x, y, z);

        float yaw = packet.getYaw();
        float pitch = packet.getPitch();

        if (packet.getFlags().contains(PlayerPositionLookS2CPacket.Flag.X_ROT)) {
            pitch += playerEntity.getPitch();
        }

        if (packet.getFlags().contains(PlayerPositionLookS2CPacket.Flag.Y_ROT)) {
            yaw += playerEntity.getYaw();
        }

        playerEntity.updatePositionAndAngles(dx, dy, dz, yaw, pitch);
        this.connection.send(new TeleportConfirmC2SPacket(packet.getTeleportId()));
        this.connection.send(new PlayerMoveC2SPacket.Full(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), playerEntity.getYaw(), playerEntity.getPitch(), false));
    }

    @Override
    public void onParticle(ParticleS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPing(PlayPingS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.sendPacket(new PlayPongC2SPacket(packet.getParameter()));
    }

    @Override
    public void onPlayerAbilities(PlayerAbilitiesS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        PlayerEntity playerEntity = this.client.player;
        playerEntity.getAbilities().flying = packet.isFlying();
        playerEntity.getAbilities().creativeMode = packet.isCreativeMode();
        playerEntity.getAbilities().invulnerable = packet.isInvulnerable();
        playerEntity.getAbilities().allowFlying = packet.allowFlying();
        playerEntity.getAbilities().setFlySpeed(packet.getFlySpeed());
        playerEntity.getAbilities().setWalkSpeed(packet.getWalkSpeed());
    }

    @Override
    public void onPlayerList(PlayerListS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
            if (packet.getAction() == PlayerListS2CPacket.Action.REMOVE_PLAYER) {
//                this.client.getSocialInteractionsManager().setPlayerOffline(entry.getProfile().getId());
                this.playerListEntries.remove(entry.getProfile().getId());
            } else {
                PlayerListEntry playerListEntry = this.playerListEntries.get(entry.getProfile().getId());
                if (packet.getAction() == PlayerListS2CPacket.Action.ADD_PLAYER) {
                    playerListEntry = new PlayerListEntry(entry);
                    this.playerListEntries.put(playerListEntry.getProfile().getId(), playerListEntry);
//                    this.client.getSocialInteractionsManager().setPlayerOnline(playerListEntry);
                }

                if (playerListEntry != null) {
                    switch (packet.getAction()) {
                        case ADD_PLAYER -> {
                            playerListEntry.setGameMode(entry.getGameMode());
                            playerListEntry.setLatency(entry.getLatency());
                            playerListEntry.setDisplayName(entry.getDisplayName());
                        }
                        case UPDATE_GAME_MODE -> playerListEntry.setGameMode(entry.getGameMode());
                        case UPDATE_LATENCY -> playerListEntry.setLatency(entry.getLatency());
                        case UPDATE_DISPLAY_NAME -> playerListEntry.setDisplayName(entry.getDisplayName());
                    }
                }
            }
        }
    }

    @Override
    public void onEntitiesDestroy(EntitiesDestroyS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        packet.getEntityIds().forEach((IntConsumer) id -> this.world.removeEntity(id, Entity.RemovalReason.DISCARDED));
    }

    @Override
    public void onRemoveEntityEffect(RemoveEntityStatusEffectS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEntitySetHeadYaw(EntitySetHeadYawS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = packet.getEntity(this.world);

        if (entity != null) {
            float f = (float)(packet.getHeadYaw() * 360) / 256.0F;
            entity.updateTrackedHeadRotation(f, 3);
        }
    }

    @Override
    public void onHeldItemChange(UpdateSelectedSlotS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        if (PlayerInventory.isValidHotbarIndex(packet.getSlot())) {
            this.client.player.getInventory().selectedSlot = packet.getSlot();
        }
    }

    @Override
    public void onScoreboardDisplay(ScoreboardDisplayS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = this.world.getEntityById(packet.id());

        if (entity != null && packet.getTrackedValues() != null) {
            entity.getDataTracker().writeUpdatedEntries(packet.getTrackedValues());
        }
    }

    @Override
    public void onVelocityUpdate(EntityVelocityUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = this.world.getEntityById(packet.getId());

        if (entity != null) {
            entity.setVelocityClient(packet.getVelocityX() / 8000.0D, packet.getVelocityY() / 8000.0D, packet.getVelocityZ() / 8000.0D);
        }
    }

    @Override
    public void onEquipmentUpdate(EntityEquipmentUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = this.world.getEntityById(packet.getId());

        if (entity != null) {
            packet.getEquipmentList().forEach(pair -> entity.equipStack(pair.getFirst(), pair.getSecond()));
        }
    }

    @Override
    public void onExperienceBarUpdate(ExperienceBarUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onHealthUpdate(HealthUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.client.player.updateHealth(packet.getHealth());
        this.client.player.getHungerManager().setFoodLevel(packet.getFood());
        this.client.player.getHungerManager().setSaturationLevel(packet.getSaturation());
    }

    @Override
    public void onTeam(TeamS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onScoreboardPlayerUpdate(ScoreboardPlayerUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPlayerSpawnPosition(PlayerSpawnPositionS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.client.world.setSpawnPos(packet.getPos(), packet.getAngle());
    }

    @Override
    public void onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.client.getWorld().setTime(packet.getTime());
        this.client.getWorld().setTimeOfDay(packet.getTimeOfDay());
    }

    @Override
    public void onPlaySound(PlaySoundS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.client.world.playSound(this.client.player, packet.getX(), packet.getY(), packet.getZ(), packet.getSound(), packet.getCategory(), packet.getVolume(), packet.getPitch());
    }

    @Override
    public void onPlaySoundFromEntity(PlaySoundFromEntityS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPlaySoundId(PlaySoundIdS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onItemPickupAnimation(ItemPickupAnimationS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEntityPosition(EntityPositionS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = this.world.getEntityById(packet.getId());

        if (entity != null) {
            double x = packet.getX();
            double y = packet.getY();
            double z = packet.getZ();
            entity.updateTrackedPosition(x, y, z);
            if (!entity.isLogicalSideForUpdatingMovement()) {
                float pitch = (float)(packet.getYaw() * 360) / 256.0F;
                float yaw = (float)(packet.getPitch() * 360) / 256.0F;
                entity.updateTrackedPositionAndAngles(x, y, z, pitch, yaw, 3, true);
                entity.setOnGround(packet.isOnGround());
            }
        }
    }

    @Override
    public void onEntityAttributes(EntityAttributesS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        Entity entity = this.world.getEntityById(packet.getEntityId());
        if (entity != null) {
            if (!(entity instanceof LivingEntity)) {
                throw new IllegalStateException("Server tried to update attributes of a non-living entity (actually: " + entity + ")");
            } else {
                AttributeContainer attributeContainer = ((LivingEntity) entity).getAttributes();

                for (EntityAttributesS2CPacket.Entry entry : packet.getEntries()) {
                    EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(entry.getId());
                    if (entityAttributeInstance == null) {
                        LOGGER.warn("Entity {} does not have attribute {}", entity, Registry.ATTRIBUTE.getId(entry.getId()));
                    } else {
                        entityAttributeInstance.setBaseValue(entry.getBaseValue());
                        entityAttributeInstance.clearModifiers();

                        for (EntityAttributeModifier entityAttributeModifier : entry.getModifiers()) {
                            entityAttributeInstance.addTemporaryModifier(entityAttributeModifier);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEntityStatusEffect(EntityStatusEffectS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSynchronizeTags(SynchronizeTagsS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        TagManager manager = TagManager.fromPacket(this.registryManager, packet.getGroups());
        Multimap<RegistryKey<? extends Registry<?>>, Identifier> missing = RequiredTagListRegistry.getMissingTags(manager);

        if (!missing.isEmpty()) {
            LOGGER.warn("Incomplete server tags, disconnecting. Missing: {}", missing);
            this.connection.disconnect(new TranslatableText("multiplayer.disconnect.missing_tags"));
        } else {
            this.tagManager = manager;
            if (!this.connection.isLocal()) {
                manager.apply();
            }

//            this.client.getSearchableContainer(SearchManager.ITEM_TAG).reload();
        }
    }

    @Override
    public void onEndCombat(EndCombatS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onEnterCombat(EnterCombatS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onDeathMessage(DeathMessageS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onDifficulty(DifficultyS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.worldProperties.setDifficulty(packet.getDifficulty());
        this.worldProperties.setDifficultyLocked(packet.isDifficultyLocked());
    }

    @Override
    public void onSetCameraEntity(SetCameraEntityS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldBorderInitialize(WorldBorderInitializeS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        WorldBorder worldBorder = this.world.getWorldBorder();
        worldBorder.setCenter(packet.getCenterX(), packet.getCenterZ());
        long l = packet.getSizeLerpTime();
        if (l > 0L) {
            worldBorder.interpolateSize(packet.getSize(), packet.getSizeLerpTarget(), l);
        } else {
            worldBorder.setSize(packet.getSizeLerpTarget());
        }

        worldBorder.setMaxRadius(packet.getMaxRadius());
        worldBorder.setWarningBlocks(packet.getWarningBlocks());
        worldBorder.setWarningTime(packet.getWarningTime());
    }

    @Override
    public void onWorldBorderInterpolateSize(WorldBorderInterpolateSizeS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldBorderSizeChanged(WorldBorderSizeChangedS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldBorderWarningTimeChanged(WorldBorderWarningTimeChangedS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldBorderWarningBlocksChanged(WorldBorderWarningBlocksChangedS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onWorldBorderCenterChanged(WorldBorderCenterChangedS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onPlayerListHeader(PlayerListHeaderS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onResourcePackSend(ResourcePackSendS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onBossBar(BossBarS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCooldownUpdate(CooldownUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onVehicleMove(VehicleMoveS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onAdvancements(AdvancementUpdateS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSelectAdvancementTab(SelectAdvancementTabS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCraftFailedResponse(CraftFailedResponseS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCommandTree(CommandTreeS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.commandDispatcher = new CommandDispatcher<>(packet.getCommandTree());
    }

    @Override
    public void onStopSound(StopSoundS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onCommandSuggestions(CommandSuggestionsS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSynchronizeRecipes(SynchronizeRecipesS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        this.recipeManager.setRecipes(packet.getRecipes());

        SearchableContainer<RecipeResultCollection> searchableContainer = this.client.getSearchableContainer(SearchManager.RECIPE_OUTPUT);
        searchableContainer.clear();

        ClientRecipeBook clientRecipeBook = this.client.player.getRecipeBook();
        clientRecipeBook.reload(this.recipeManager.values());

        Objects.requireNonNull(searchableContainer);
        clientRecipeBook.getOrderedResults().forEach(searchableContainer::add);
        searchableContainer.reload();
    }

    @Override
    public void onLookAt(LookAtS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onTagQuery(NbtQueryResponseS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onLightUpdate(LightUpdateS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);

        int x = packet.getChunkX();
        int z = packet.getChunkZ();
        LightingProvider provider = this.world.getChunkManager().getLightingProvider();

        BitSet skyMask = packet.getSkyLightMask();
        BitSet filledSkyMask = packet.getFilledSkyLightMask();
        Iterator<byte[]> skyIterator = packet.getSkyLightUpdates().iterator();
        for(int i = 0; i < provider.getHeight(); ++i) {
            int y = provider.getBottomY() + i;
            boolean maskState = skyMask.get(i);
            boolean filledMaskState = filledSkyMask.get(i);
            if (maskState || filledMaskState) {
                ChunkNibbleArray nibble = maskState ? new ChunkNibbleArray(skyIterator.next().clone()) : new ChunkNibbleArray();
                provider.enqueueSectionData(LightType.SKY, ChunkSectionPos.from(x, y, z), nibble, packet.isNotEdge());
            }
        }

        BitSet blockMask = packet.getBlockLightMask();
        BitSet filledBlockMask = packet.getFilledBlockLightMask();
        Iterator<byte[]> blockIterator = packet.getBlockLightUpdates().iterator();
        for(int i = 0; i < provider.getHeight(); ++i) {
            int y = provider.getBottomY() + i;
            boolean maskState = blockMask.get(i);
            boolean filledMaskState = filledBlockMask.get(i);
            if (maskState || filledMaskState) {
                ChunkNibbleArray nibble = maskState ? new ChunkNibbleArray(blockIterator.next().clone()) : new ChunkNibbleArray();
                provider.enqueueSectionData(LightType.BLOCK, ChunkSectionPos.from(x, y, z), nibble, packet.isNotEdge());
            }
        }
    }

    @Override
    public void onOpenWrittenBook(OpenWrittenBookS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onOpenScreen(OpenScreenS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSetTradeOffers(SetTradeOffersS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onChunkLoadDistance(ChunkLoadDistanceS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onChunkRenderDistanceCenter(ChunkRenderDistanceCenterS2CPacket packet) {
        NetworkThreadUtils.forceMainThread(packet, this, this.client);
        this.world.getChunkManager().setChunkMapCenter(packet.getChunkX(), packet.getChunkZ());
    }

    @Override
    public void onPlayerActionResponse(PlayerActionResponseS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onOverlayMessage(OverlayMessageS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onSubtitle(SubtitleS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onTitle(TitleS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onTitleFade(TitleFadeS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onTitleClear(ClearTitleS2CPacket packet) {
        LOGGER.debug("Packet Stub: {}", packet);
    }

    @Override
    public void onDisconnected(Text reason) {

    }

    @Override
    public ClientConnection getConnection() {
        return client.getConnection();
    }

    public int getChunkLoadDistance() {
        return chunkLoadDistance;
    }

    public DynamicRegistryManager getRegistryManager() {
        return this.registryManager;
    }

    public TagManager getTagManager() {
        return this.tagManager;
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    /**
     * {@link ClientPlayNetworkHandler#getPlayerListEntry(UUID)}
     */
    @SuppressWarnings("unused")
    @Nullable
    public PlayerListEntry getPlayerListEntry(UUID uuid) {
        return this.playerListEntries.get(uuid);
    }
}
