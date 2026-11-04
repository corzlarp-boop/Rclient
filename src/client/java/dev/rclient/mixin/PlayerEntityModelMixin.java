package dev.rclient.mixin;

import dev.rclient.visual.EmoteModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the active emote's pose offsets to YOUR player model only (client-side animation, nothing is sent).
 * require = 0 so a signature change in a future Minecraft version disables emote posing instead of crashing.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {
    private static final float[] POSE = new float[10];

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
    private void rclient$emote(AvatarRenderState state, CallbackInfo ci) {
        if (!EmoteModule.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerModel self = (PlayerModel) (Object) this;
        EmoteModule.pose(EmoteModule.playTime, POSE);
        self.head.xRot += POSE[0];
        self.head.yRot += POSE[1];
        self.rightArm.xRot += POSE[2];
        self.rightArm.zRot += POSE[3];
        self.leftArm.xRot += POSE[4];
        self.leftArm.zRot += POSE[5];
        self.rightLeg.xRot += POSE[6];
        self.leftLeg.xRot += POSE[7];
        self.body.yRot += POSE[8];
        self.body.y += -POSE[9] * 16f;
    }
}
