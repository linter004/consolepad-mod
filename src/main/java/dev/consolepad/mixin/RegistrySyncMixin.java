package dev.consolepad.mixin;

import dev.consolepad.ConsolePad;
import dev.consolepad.split.SecondaryPlayers;
import dev.consolepad.split.SplitDiag;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handlers.ClientPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.FrozenRegistrySyncCompletedPayload;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Un jugador extra entra al mismo servidor integrado, en la misma JVM: sus registros son los mismos. Aplicar la sincronizacion de
 * NeoForge (RegistryManager.applySnapshot) vacia y reconstruye el mapa global de ids de estados de bloque mientras el hilo del
 * servidor serializa chunks para P1: P1 recibia ids -1 y se desconectaba ("No value with id -1"). Para los extra se omite y se
 * contesta igual que si se hubiera aplicado.
 */
@Mixin(value = ClientPayloadHandler.class, remap = false)
public abstract class RegistrySyncMixin {
    @Shadow @Final private static Set<ResourceLocation> toSynchronize;
    @Shadow @Final private static Map<ResourceLocation, RegistrySnapshot> synchronizedRegistries;

    @Inject(method = "handle(Lnet/neoforged/neoforge/network/payload/FrozenRegistrySyncCompletedPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"), cancellable = true)
    private static void consolepad$skipForExtras(FrozenRegistrySyncCompletedPayload payload, IPayloadContext context, CallbackInfo ci) {
        if (!SecondaryPlayers.isSecondary(context.connection())) return;
        toSynchronize.clear();
        synchronizedRegistries.clear();
        SplitDiag.registrySyncSkipped++;
        ConsolePad.LOGGER.info("[split] sincronizacion de registros de un jugador extra omitida (mismos registros que P1)");
        context.reply(FrozenRegistrySyncCompletedPayload.INSTANCE);
        ci.cancel();
    }
}
