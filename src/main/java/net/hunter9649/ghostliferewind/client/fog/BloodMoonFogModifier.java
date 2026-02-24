package net.hunter9649.ghostliferewind.client.fog;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hunter9649.ghostliferewind.GhostLifeRewind;
import net.hunter9649.ghostliferewind.client.BloodMoonClientUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BloodMoonFogModifier extends FogModifier {
	private static final float FOG_END_BLOCKS = 200.0F;
	private static final AtmosphericFogModifier BASE = new AtmosphericFogModifier();

	@Override
	public void applyStartEndModifier(FogData data, Camera camera, ClientWorld clientWorld, float viewDistanceBlocks, RenderTickCounter renderTickCounter) {
		float factor = BloodMoonClientUtil.getBloodMoonFactor(clientWorld);
		FogData base = new FogData();
		BASE.applyStartEndModifier(base, camera, clientWorld, viewDistanceBlocks, renderTickCounter);

		float bloodStart = 0.0F;
		float bloodEnd = FOG_END_BLOCKS;

		data.environmentalStart = MathHelper.lerp(factor, base.environmentalStart, bloodStart);
		data.environmentalEnd = MathHelper.lerp(factor, base.environmentalEnd, bloodEnd);
		data.skyEnd = MathHelper.lerp(factor, base.skyEnd, bloodEnd);
		data.cloudEnd = MathHelper.lerp(factor, base.cloudEnd, bloodEnd);
	}

	@Override
	public int getFogColor(ClientWorld world, Camera camera, int viewDistance, float skyDarkness) {
		float factor = BloodMoonClientUtil.getBloodMoonFactor(world);
		int baseColor = BASE.getFogColor(world, camera, viewDistance, skyDarkness);
		return BloodMoonClientUtil.lerpColor(baseColor, BloodMoonClientUtil.BLOOD_COLOR, factor);
	}

	@Override
	public boolean shouldApply(@Nullable CameraSubmersionType submersionType, Entity cameraEntity) {
		if (!GhostLifeRewind.isBloodMoon || submersionType != CameraSubmersionType.ATMOSPHERIC) {
			return false;
		}
		if (!(cameraEntity.getEntityWorld() instanceof ClientWorld clientWorld)) {
			return false;
		}
		return BloodMoonClientUtil.getBloodMoonFactor(clientWorld) > 0.0F;
	}
}
