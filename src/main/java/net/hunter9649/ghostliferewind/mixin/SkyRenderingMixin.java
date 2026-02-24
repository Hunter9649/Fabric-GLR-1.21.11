package net.hunter9649.ghostliferewind.mixin;

import net.hunter9649.ghostliferewind.GhostLifeRewind;
import net.hunter9649.ghostliferewind.client.BloodMoonClientUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void ghostliferewind$tintSkyForBloodMoon(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (!GhostLifeRewind.isBloodMoon) {
			return;
		}
		float factor = BloodMoonClientUtil.getBloodMoonFactor(world);
		if (factor <= 0.0F) {
			return;
		}
		state.skyColor = BloodMoonClientUtil.lerpColor(state.skyColor, BloodMoonClientUtil.BLOOD_COLOR, factor);
		state.sunriseAndSunsetColor = BloodMoonClientUtil.lerpColorPreserveAlpha(
			state.sunriseAndSunsetColor,
			BloodMoonClientUtil.BLOOD_COLOR,
			factor
		);
	}
}
