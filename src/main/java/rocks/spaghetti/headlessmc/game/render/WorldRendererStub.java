package rocks.spaghetti.headlessmc.game.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import rocks.spaghetti.headlessmc.util.ReflectionUtil;

/**
 * Proxy class
 * @see WorldRenderer
 */
@SuppressWarnings("unused")
public class WorldRendererStub {
    private WorldRendererStub() { }

    public static WorldRenderer get() {
        return ReflectionUtil.createProxy(WorldRenderer.class, new WorldRendererStub());
    }


    /**
     * {@link WorldRenderer#updateBlock(BlockView, BlockPos, BlockState, BlockState, int)}
     */
    public void updateBlock(BlockView world, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        // stub
    }

    /**
     * {@link WorldRenderer#addParticle(ParticleEffect, boolean, double, double, double, double, double, double)}
     */
    public void addParticle(ParticleEffect parameters, boolean shouldAlwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        // stub
    }

    /**
     * {@link WorldRenderer#addParticle(ParticleEffect, boolean, boolean, double, double, double, double, double, double)}
     */
    public void addParticle(ParticleEffect parameters, boolean shouldAlwaysSpawn, boolean important, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        // stub
    }

    /**
     * {@link WorldRenderer#scheduleBlockRerenderIfNeeded(BlockPos, BlockState, BlockState)}
     */
    public void scheduleBlockRerenderIfNeeded(BlockPos pos, BlockState old, BlockState updated) {
        // stub
    }

    /**
     * {@link WorldRenderer#processGlobalEvent(int, BlockPos, int)}
     */
    public void processGlobalEvent(int eventId, BlockPos pos, int data) {
        // stub
    }

    /**
     * {@link WorldRenderer#processWorldEvent(PlayerEntity, int, BlockPos, int)}
     */
    public void processWorldEvent(PlayerEntity source, int eventId, BlockPos pos, int data) {
        // stub
    }
}
