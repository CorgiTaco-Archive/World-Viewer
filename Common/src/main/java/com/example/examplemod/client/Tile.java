package com.example.examplemod.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Function;

import static com.example.examplemod.client.WorldViewingScreen.*;

public class Tile {
    private final BoundingBox worldBoundingBox;

    public final WorldViewingScreen.DataAtPosition[][] dataAtPositions;

    public Tile(Object2IntOpenHashMap<Holder<Biome>> color, int worldTileX, int worldTileZ, Function<BlockPos, Holder<Biome>> biomeGetter) {
        BoundingBox boundingBox = new BoundingBox(
            tileToBlock(worldTileX), 0, tileToBlock(worldTileZ),
            tileToMaxBlock(worldTileX), 0, tileToMaxBlock(worldTileZ)
        );

        this.worldBoundingBox = boundingBox;
        dataAtPositions = new WorldViewingScreen.DataAtPosition[boundingBox.getXSpan() - 1][boundingBox.getZSpan() - 1];
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < boundingBox.getXSpan() - 1; x++) {
            for (int z = 0; z < boundingBox.getZSpan() - 1; z++) {
                mutableBlockPos.set(x + boundingBox.minX(), 63, z + boundingBox.minZ());
                Holder<Biome> biomeHolder = biomeGetter.apply(mutableBlockPos);
                dataAtPositions[x][z] = new WorldViewingScreen.DataAtPosition(color.getInt(biomeHolder), getKey(biomeHolder.unwrapKey().orElseThrow().location()));
            }
        }
    }

    public void render(PoseStack stack, BufferBuilder bufferbuilder, int screenTileMinX, int screenTileMinZ) {
        for (int x = 0; x < this.worldBoundingBox.getXSpan() - 1; x++) {
            for (int z = 0; z < this.worldBoundingBox.getZSpan() - 1; z++) {
                WorldViewingScreen.DataAtPosition dataAtPosition = this.dataAtPositions[x][z];
                int fillMinX = screenTileMinX + x;
                int fillMinZ = screenTileMinZ + z;
                fill(stack, fillMinX, fillMinZ, fillMinX + 1, fillMinZ + 1, dataAtPosition.color(), bufferbuilder);
            }
        }
    }
}