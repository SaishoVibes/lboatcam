package microwavedram.lboatcam.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static microwavedram.lboatcam.config.BoatCamConfig.getConfig;

@Mixin(BoatEntity.class)
public class BoatEntityMixin {
    @Inject(method = "clampPassengerYaw", at = @At("INVOKE"), cancellable = true)
    private void clampPassengerYaw(Entity entity, CallbackInfo info) {
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