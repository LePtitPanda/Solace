package com.solace.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = ChunkBuilder.class, remap = false)
public interface EmbeddiumChunkBuilderAccessor {
    @Accessor("threads")
    List<Thread> solace$getThreads();

    @Accessor("busyThreadCount")
    AtomicInteger solace$getBusyThreadCount();
}
