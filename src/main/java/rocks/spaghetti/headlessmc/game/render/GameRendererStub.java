package rocks.spaghetti.headlessmc.game.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import rocks.spaghetti.headlessmc.util.ReflectionUtil;

/**
 * Proxy class
 * @see GameRenderer
 */
@SuppressWarnings("unused")
public class GameRendererStub {
    private final Camera camera;

    private GameRendererStub() {
        this.camera = new Camera();
    }

    public static GameRenderer get() {
        return ReflectionUtil.createProxy(GameRenderer.class, new GameRendererStub());
    }


    /**
     * {@link GameRenderer#getCamera()}
     */
    public Camera getCamera() {
        return camera;
    }
}
