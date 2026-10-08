package dev.consolepad.mixin;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Estado estatico de la niebla (color y transicion entre biomas): cada region lo lleva aparte para que no parpadee entre jugadores en biomas distintos. */
@Mixin(FogRenderer.class)
public interface FogStateAccessor {
    @Accessor("fogRed")
    static float consolepad$getRed() {
        throw new AssertionError();
    }

    @Accessor("fogRed")
    static void consolepad$setRed(float v) {
        throw new AssertionError();
    }

    @Accessor("fogGreen")
    static float consolepad$getGreen() {
        throw new AssertionError();
    }

    @Accessor("fogGreen")
    static void consolepad$setGreen(float v) {
        throw new AssertionError();
    }

    @Accessor("fogBlue")
    static float consolepad$getBlue() {
        throw new AssertionError();
    }

    @Accessor("fogBlue")
    static void consolepad$setBlue(float v) {
        throw new AssertionError();
    }

    @Accessor("targetBiomeFog")
    static int consolepad$getTarget() {
        throw new AssertionError();
    }

    @Accessor("targetBiomeFog")
    static void consolepad$setTarget(int v) {
        throw new AssertionError();
    }

    @Accessor("previousBiomeFog")
    static int consolepad$getPrevious() {
        throw new AssertionError();
    }

    @Accessor("previousBiomeFog")
    static void consolepad$setPrevious(int v) {
        throw new AssertionError();
    }

    @Accessor("biomeChangedTime")
    static long consolepad$getChanged() {
        throw new AssertionError();
    }

    @Accessor("biomeChangedTime")
    static void consolepad$setChanged(long v) {
        throw new AssertionError();
    }
}
