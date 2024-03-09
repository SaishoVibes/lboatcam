package shizuya.sboatcam;

import shizuya.sboatcam.config.BoatCamConfig;
import shizuya.sboatcam.event.LookDirectionChangingEvent;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.List;

import static shizuya.sboatcam.config.BoatCamConfig.getConfig;
import static java.lang.Math.*;
import static net.minecraft.client.util.InputUtil.Type.KEYSYM;
import static net.minecraft.util.Formatting.GREEN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_N;

public class BoatCamMod implements ModInitializer, LookDirectionChangingEvent {
    // key binds
    private final KeyBinding TOGGLE = new KeyBinding("key.sboatcam.toggle", KEYSYM, -1, "sBoatCam");
    private final KeyBinding LOOK_BEHIND = new KeyBinding("key.sboatcam.lookbehind", KEYSYM, GLFW_KEY_B, "sBoatCam");
    private final KeyBinding RESET_CAMERA = new KeyBinding("key.sboatcam.resetcamera", KEYSYM, GLFW_KEY_N, "sBoatCam");

    // things to remember temporarily
    private Perspective perspective;
    private float previousYaw;
    private double speed;
    private double offset;
    private double scale;

    // states
    private boolean lookingBehind;

    @Override
    public void onInitialize() {
        AutoConfig.register(BoatCamConfig.class, JanksonConfigSerializer::new);
        KeyBindingHelper.registerKeyBinding(TOGGLE);
        KeyBindingHelper.registerKeyBinding(LOOK_BEHIND);
        KeyBindingHelper.registerKeyBinding(RESET_CAMERA);
        ClientTickEvents.START_WORLD_TICK.register(this::onClientEndWorldTick);
        LookDirectionChangingEvent.EVENT.register(this);
        AutoConfig.getGuiRegistry(BoatCamConfig.class).registerPredicateTransformer(
            (guis, s, f, c, d, g) -> dropdownToEnumList(guis, f),
            field -> BoatCamConfig.Perspective.class.isAssignableFrom(field.getType())
        );
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<AbstractConfigListEntry> dropdownToEnumList(List<AbstractConfigListEntry> guis, Field field) {
        return guis.stream()
            .filter(DropdownBoxEntry.class::isInstance)
            .map(DropdownBoxEntry.class::cast)
            // transform dropdown menu into enum list
            .map(dropdown -> ConfigEntryBuilder.create()
                .startEnumSelector(dropdown.getFieldName(), BoatCamConfig.Perspective.class, (BoatCamConfig.Perspective) dropdown.getValue())
                .setDefaultValue((BoatCamConfig.Perspective) dropdown.getDefaultValue().orElse(null))
                .setSaveConsumer(p -> {
                    try {
                        field.set(getConfig(), p);
                    } catch (IllegalAccessException ignored) { }
                })
                .setEnumNameProvider(perspective -> switch ((BoatCamConfig.Perspective) perspective) {
                    case FIRST_PERSON -> Text.translatable("text.autoconfig.sboatcam.option.perspective.firstPerson");
                    case THIRD_PERSON -> Text.translatable("text.autoconfig.sboatcam.option.perspective.thirdPerson");
                    case NONE -> Text.translatable("text.autoconfig.sboatcam.option.perspective.none");
                })
                .build())
            .map(AbstractConfigListEntry.class::cast)
            .toList();
    }

    private void onClientEndWorldTick(ClientWorld world) {
        MinecraftClient client = MinecraftClient.getInstance();
        scale = (double) client.getWindow().getWidth() / client.options.getFov().getValue();
        // key bind logic
        if (TOGGLE.wasPressed()) {
            getConfig().toggleBoatMode();
            client.inGameHud.setOverlayMessage(Text.literal(getConfig().isBoatcam() ? "BoatCam enabled" : "BoatCam disabled").styled(s -> s.withColor(GREEN)), false);
        }
        // camera logic
        assert client.player != null;
        if (getConfig().isBoatcam() && client.player.getVehicle() instanceof BoatEntity boat) {
            // first tick riding in boat mode
            if (this.perspective == null) {
                // fix pitch if configured
                if (getConfig().shouldFixPitch()) client.player.setPitch(getConfig().getPitch());
                // init look behind
                // lookingBehind = false;
                // save perspective
                this.perspective = client.options.getPerspective();
                this.previousYaw = boat.getYaw();
                // set perspective
                switch (getConfig().getPerspective()) {
                    case FIRST_PERSON -> client.options.setPerspective(Perspective.FIRST_PERSON);
                    case THIRD_PERSON -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }
            }
            if (RESET_CAMERA.isPressed()) {
                this.offset = 0;
            }
            calculateYaw(client.player, boat);
            // if pressed state changed
            if (LOOK_BEHIND.isPressed() != this.lookingBehind) {
                // save state
                this.lookingBehind = LOOK_BEHIND.isPressed();
                // handle change
                invertPitch();
                if (this.lookingBehind) {
                    // set look back perspective
                    client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                } else {
                    // reset perspective
                    switch (getConfig().getPerspective()) {
                        case FIRST_PERSON -> client.options.setPerspective(Perspective.FIRST_PERSON);
                        case THIRD_PERSON -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                        default -> resetPerspective();
                    }
                }
            }
        } else {
            // first tick after disabling boat mode or leaving boat
            if (this.perspective != null) {
                resetPerspective();
                // invert pitch if looking behind
                if (this.lookingBehind) {
                    invertPitch();
                    this.lookingBehind = false;
                }
                this.offset = 0;
            }
        }
    }

    private void invertPitch() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;
        player.setPitch(-player.getPitch());
    }

    private void resetPerspective() {
        if (this.perspective != null) {
            MinecraftClient.getInstance().options.setPerspective(this.perspective);
            this.perspective = null;
        }
    }

    private void calculateYaw(ClientPlayerEntity player, BoatEntity boat) {
        // yaw calculations
        float yaw = boat.getYaw();
        Vec3d velocity = boat.getVelocity().multiply(1, 0, 1);
        this.speed = velocity.length() * 20;
        if (this.speed > 0.4) {
            float direction = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
            if (getConfig().isOldBoatcam()) {
                yaw += normaliseAngle(direction - yaw) * min(1, (float) this.speed * getConfig().getStrength() / (60 * (1 - getConfig().getStrength())));
            } else {
                float slipAngle = (float) Math.toRadians(normaliseAngle(direction - yaw));
                yaw += Math.toDegrees(slipAngle - atan2(sin(slipAngle) * 16 * 1.6 * (1 - getConfig().getStrength()), (cos(slipAngle) * 16 * 1.6 * (1 - getConfig().getStrength()) + this.speed * getConfig().getStrength())));
            }
            yaw = previousYaw + normaliseAngle(yaw - previousYaw) * (1 - getConfig().getSmoothening());
            player.setYaw(yaw + (float) offset);
            if (getConfig().shouldFixPitch()) {
                player.setPitch(getConfig().getPitch());
                if (this.lookingBehind) {
                    invertPitch();
                }
            }
        }
        // save pos and yaw
        previousYaw = yaw;
    }

    @Override
    public boolean onLookDirectionChanging(double dx, double dy) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && !(player.getVehicle() instanceof BoatEntity)) return false;
        if (getConfig().isBoatcam() && this.speed >= 0.4) {
            if (!getConfig().shouldLockYaw()) this.offset += dx / scale;
            if ((getConfig().shouldLockYaw() && dx != 0) || (getConfig().shouldFixPitch() && dy != 0)) {
                // prevent horizontal camera movement and cancel camera change by returning true
                // prevent vertical movement as well if configured
                player.changeLookDirection(getConfig().shouldLockYaw() ? 0 : dx, getConfig().shouldFixPitch() ? 0 : dy);
                return true;
            }
        }
        return false;
    }

    public static float normaliseAngle(float angle) {
        return angle - (float) Math.floor(angle / 360 + 0.5) * 360;
    }
}