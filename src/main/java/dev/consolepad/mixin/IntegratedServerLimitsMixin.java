package dev.consolepad.mixin;

import dev.consolepad.split.PerfLimits;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** El servidor integrado tambien manda y simula menos chunks por jugador cuando la distancia automatica esta activa. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerLimitsMixin {
    @ModifyArg(method = "tickServer", index = 1, at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0))
    private int consolepad$serverViewDistance(int requested) {
        return PerfLimits.viewDistance(requested);
    }

    @ModifyArg(method = "tickServer", index = 1, at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 1))
    private int consolepad$serverSimulationDistance(int requested) {
        return PerfLimits.simulationDistance(requested);
    }
}
