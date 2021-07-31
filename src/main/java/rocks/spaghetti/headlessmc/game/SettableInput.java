package rocks.spaghetti.headlessmc.game;

import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public class SettableInput extends Input {
    private final ClientPlayerEntity player;

    private boolean jumpingState;
    private boolean sneakingState;
    private boolean sprintingState;
    private float forwardSpeed;
    private float sidewaysSpeed;

    public SettableInput(ClientPlayerEntity player) {
        this.player = player;
    }

    @Override
    public void tick(boolean slowDown) {
        this.jumping = jumpingState;
        this.sneaking = sneakingState;
        this.player.setSprinting(sprintingState);
        this.movementForward = forwardSpeed;
        this.movementSideways = sidewaysSpeed;
    }

    public void clear() {
        jumping(false);
        sneaking(false);
        sprinting(false);
        movement(0, 0);
    }

    public void jumping(boolean state) {
        this.jumpingState = state;
    }

    public void sneaking(boolean state) {
        this.sneakingState = state;
    }

    public void sprinting(boolean state) {
        this.sprintingState = state;
    }

    public void movement(float forward, float sideways) {
        this.forwardSpeed = MathHelper.clamp(forward, -1.0F, 1.0F);
        this.sidewaysSpeed = MathHelper.clamp(sideways, -1.0F, 1.0F);
    }

    public void look(float pitch, float yaw) {
        player.setPitch(pitch);
        player.setYaw(yaw);
    }
}
