package shizuya.sboatcam.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static shizuya.sboatcam.BoatCamMod.getMouseSteer;

@Mixin(BoatEntity.class)
public class BoatEntityMixin {
    @Inject(method = "copyEntityData", at = @At("INVOKE"), cancellable = true)
    private void copyEntityData(Entity entity, CallbackInfo info) {
        // disable turn limit
        if (entity.equals(MinecraftClient.getInstance().player)) {
            // just copied the code and cancelled, easier than making a complicated mixin
            //noinspection ConstantConditions
            float yaw = ((Entity) (Object) this).getYaw();
            entity.setBodyYaw(yaw);
            entity.setHeadYaw(entity.getYaw());
            info.cancel();
        }
    }
}

@Mixin(BoatEntity.class)
abstract class PaddleMixin {
    @Shadow private boolean pressingLeft;
    @Shadow private boolean pressingRight;
    @Shadow private boolean pressingForward;
    @Shadow private boolean pressingBack;
    @Shadow private float yawVelocity;

    @Redirect(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/BoatEntity;updatePaddles()V"))
    private void replaceUpdatePaddles(BoatEntity instance) {
        if (instance.hasPassengers()) {
            float acceleration = 0.0f;
            float steering = Math.min(1.0f, Math.max(-1.0f, (float) getMouseSteer() + (this.pressingRight ? 1.0f : 0.0f) + (this.pressingLeft ? -1.0f : 0.0f)));
            this.yawVelocity += steering;

            if (!this.pressingForward && !this.pressingBack && steering != 0.0F) {
                acceleration += 0.005F * Math.abs(steering);
            }
            instance.setYaw(instance.getYaw() + this.yawVelocity);
            if (this.pressingForward) {
                acceleration += 0.04F;
            }
            if (this.pressingBack) {
                acceleration -= 0.005F;
            }

            Vec3d thrust = new Vec3d((double) (Math.sin(Math.toRadians(-instance.getYaw())) * acceleration), 0.0D, (double) (Math.cos(Math.toRadians(instance.getYaw())) * acceleration));
            instance.setVelocity(instance.getVelocity().add(thrust));
            instance.setPaddleMovings(steering > 0 || this.pressingForward, steering < 0 || this.pressingForward);
        }
    }
}