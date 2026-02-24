package net.hunter9649.ghostliferewind.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

public final class BloodMoonClientUtil {
	public static final int BLOOD_COLOR = 0xFF8B0000;

	private BloodMoonClientUtil() {
	}

	public static float getBloodMoonFactor(ClientWorld world) {
		long time = world.getTimeOfDay() % 24000L;
		if (time < 12000L) {
			return 0.0F;
		}
		if (time < 13000L) {
			return (time - 12000L) / 1000.0F;
		}
		if (time < 23000L) {
			return 1.0F;
		}
		if (time < 24000L) {
			return 1.0F - (time - 23000L) / 1000.0F;
		}
		return 0.0F;
	}

	public static int lerpColor(int from, int to, float t) {
		return lerpColor(from, to, t, false);
	}

	public static int lerpColorPreserveAlpha(int from, int to, float t) {
		return lerpColor(from, to, t, true);
	}

	private static int lerpColor(int from, int to, float t, boolean preserveAlpha) {
		float clamped = MathHelper.clamp(t, 0.0F, 1.0F);
		int fa = (from >> 24) & 0xFF;
		int fr = (from >> 16) & 0xFF;
		int fg = (from >> 8) & 0xFF;
		int fb = from & 0xFF;
		int ta = (to >> 24) & 0xFF;
		int tr = (to >> 16) & 0xFF;
		int tg = (to >> 8) & 0xFF;
		int tb = to & 0xFF;
		int a = preserveAlpha ? fa : MathHelper.floor(MathHelper.lerp(clamped, fa, ta));
		int r = MathHelper.floor(MathHelper.lerp(clamped, fr, tr));
		int g = MathHelper.floor(MathHelper.lerp(clamped, fg, tg));
		int b = MathHelper.floor(MathHelper.lerp(clamped, fb, tb));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
