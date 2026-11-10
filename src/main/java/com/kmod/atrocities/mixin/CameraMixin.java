package com.kmod.atrocities.mixin;

import com.kmod.atrocities.client.cinematic.CinematicDirector;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entrega a camera ao {@link CinematicDirector} durante as cutscenes dos chefes (somente cliente). */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void atrocities$cinematic(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                      float partialTick, CallbackInfo ci) {
        if (!CinematicDirector.isActive()) {
            return;
        }
        CinematicDirector.Pose pose = CinematicDirector.pose(getPosition(), getYRot(), getXRot(), partialTick);
        if (pose != null) {
            setRotation(pose.yaw(), pose.pitch());
            setPosition(pose.position());
            if (pose.detached()) {
                this.detached = true;
            }
        }
    }
}
