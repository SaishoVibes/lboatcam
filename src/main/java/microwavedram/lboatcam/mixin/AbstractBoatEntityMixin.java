package microwavedram.lboatcam.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoatEntity.class)
public class AbstractBoatEntityMixin {

    @Inject(method = "clampPassengerYaw", at = @At("HEAD"), cancellable = true)
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