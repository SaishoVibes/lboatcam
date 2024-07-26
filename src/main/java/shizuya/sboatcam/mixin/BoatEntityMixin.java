package shizuya.sboatcam.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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