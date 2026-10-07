package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LevelRenderer de P2. Antes de {@link #goLive} (durante el login, cuando su ClientLevel aun no esta listo) solo absorbe
 * los avisos de su ClientLevel sin tocar el renderer de P1; una vez "vivo" se comporta como un LevelRenderer normal:
 * prepara y dibuja los chunks de P2 con sus propios buffers (RenderBuffers propio, para no compartir el pool de P1).
 * Su setLevel nunca debe llamarse con el nivel de P1.
 */
public final class SplitLevelRenderer extends LevelRenderer {
    private volatile boolean live;

    SplitLevelRenderer(Minecraft mc) {
        super(mc, mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(), new RenderBuffers(2));
    }

    public boolean isLive() {
        return live;
    }

    /** Hilo de render. Prepara la rejilla de secciones para el nivel de P2. */
    void goLive(ClientLevel level) {
        live = true;
        super.setLevel(level);
    }

    /** Libera los buffers de GPU de P2 (al salir P2). */
    void release() {
        if (live) {
            live = false;
            super.setLevel(null);
        }
        close();
    }

    @Override public void setLevel(ClientLevel level) {
        if (live) super.setLevel(level);
    }

    @Override public void onChunkLoaded(ChunkPos chunkPos) {
        if (live) super.onChunkLoaded(chunkPos);
    }

    @Override public void blockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (live) super.blockChanged(level, pos, oldState, newState, flags);
    }

    @Override public void setBlockDirty(BlockPos pos, BlockState oldState, BlockState newState) {
        if (live) super.setBlockDirty(pos, oldState, newState);
    }

    @Override public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) {
        if (live) super.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
    }

    @Override public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        if (live) super.destroyBlockProgress(breakerId, pos, progress);
    }

    @Override public void globalLevelEvent(int type, BlockPos pos, int data) {
        if (live) super.globalLevelEvent(type, pos, data);
    }

    @Override public void levelEvent(int type, BlockPos pos, int data) {
        if (live) super.levelEvent(type, pos, data);
    }

    // Las particulas del cliente de P2 no se muestran (el ParticleEngine es de P1); se descartan siempre.
    @Override public void addParticle(ParticleOptions options, boolean force, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed) { }

    @Override public void addParticle(ParticleOptions options, boolean force, boolean decreased, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed) { }
}
