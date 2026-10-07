package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LevelRenderer de P2 hasta el paso 4. Nunca recibe setLevel (LevelRenderer.setLevel tocaria el EntityRenderDispatcher
 * compartido con P1): solo absorbe los avisos que ClientLevel le manda, que de otro modo irian al renderer de P1.
 */
final class DormantLevelRenderer extends LevelRenderer {
    DormantLevelRenderer(Minecraft mc) {
        super(mc, mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(), mc.renderBuffers());
    }

    @Override public void onChunkLoaded(ChunkPos chunkPos) { }
    @Override public void blockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    @Override public void setBlockDirty(BlockPos pos, BlockState oldState, BlockState newState) { }
    @Override public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) { }
    @Override public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) { }
    @Override public void globalLevelEvent(int type, BlockPos pos, int data) { }
    @Override public void levelEvent(int type, BlockPos pos, int data) { }
    @Override public void addParticle(ParticleOptions options, boolean force, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed) { }
    @Override public void addParticle(ParticleOptions options, boolean force, boolean decreased, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed) { }
    @Override public void setLevel(ClientLevel level) { }
}
