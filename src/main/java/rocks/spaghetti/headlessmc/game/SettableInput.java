package rocks.spaghetti.headlessmc.game;

import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public class SettableInput extends Input {
    private final ClientPlayerEntity player;

    public SettableInput(ClientPlayerEntity player) {
        this.player = player;
    }

    @Override
    public void tick(boolean slowDown) {
        // dont need to tick anything
    }

    public void clear() {
        jumping(false);
        sneaking(false);
        sprinting(false);
        movement(0, 0);
    }

    public void jumping(boolean state) {
        this.jumping = state;
    }

    public void sneaking(boolean state) {
        this.sneaking = state;
    }

    public void sprinting(boolean state) {
        player.setSprinting(state);
    }

    public void movement(float forward, float sideways) {
        this.movementForward = MathHelper.clamp(forward, -1.0F, 1.0F);
        this.movementSideways = MathHelper.clamp(sideways, -1.0F, 1.0F);
    }

    public void look(float pitch, float yaw) {
        player.setPitch(pitch);
        player.setYaw(yaw);
    }
}
