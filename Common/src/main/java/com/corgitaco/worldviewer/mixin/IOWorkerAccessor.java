package com.corgitaco.worldviewer.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(IOWorker.class)
public interface IOWorkerAccessor {


    @Invoker("<init>")
    static IOWorker makeStorage(Path folder, boolean sync, String name) {
        throw new Error("Mixin did not apply!");
    }
}
