package dev.consolepad.mixin;

import dev.consolepad.split.CustomSkins;
import dev.consolepad.split.Profiles;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un jugador extra con un perfil de skin propia (PNG de config/consolepad/skins) la lleva en lugar de la de su cuenta, para todos los que lo ven. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void consolepad$customSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        Profiles.Profile p = Profiles.byId(((PlayerInfo) (Object) this).getProfile().getName());
        if (p == null || p.customSkin == null) return;
        PlayerSkin skin = CustomSkins.skinOf(p);
        if (skin != null) cir.setReturnValue(skin);
    }
}
