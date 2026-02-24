package net.hunter9649.ghostliferewind.mixin;

import net.hunter9649.ghostliferewind.client.fog.BloodMoonFogModifier;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow @Final @Mutable
	private static List<FogModifier> FOG_MODIFIERS;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void ghostliferewind$addBloodMoonFogModifier(CallbackInfo ci) {
		FOG_MODIFIERS.add(0, new BloodMoonFogModifier());
	}
}
