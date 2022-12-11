package com.corgitaco.worldviewer.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
public interface RegionFileStorageAccessor {

    @Invoker("<init>")
    static RegionFileStorage makeStorage(Path folder, boolean sync) {
        throw new Error("Mixin did not apply!");
    }

    @Invoker("write")
    void _write(ChunkPos pos, @Nullable CompoundTag tag) throws IOException;
}
