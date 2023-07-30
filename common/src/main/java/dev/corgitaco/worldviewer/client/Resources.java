package dev.corgitaco.worldviewer.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

import static org.lwjgl.system.MemoryUtil.memCalloc;
import static org.lwjgl.system.MemoryUtil.memRealloc;

public final class Resources {

    private Resources() {}

    public static InputStream getResource(String path) throws IOException {
        return Resources.class.getClassLoader().getResource(path).openStream();
    }

    public static String readString(String path) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(getResource(path)))) {
            var builder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            return builder.toString();
        }
    }

    public static String readString(InputStream inputStream) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
            var builder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            return builder.toString();
        }
    }

    public static String readString(ResourceManager manager, ResourceLocation location) throws IOException {
        try (var reader = manager.openAsReader(location)) {
            var builder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            return builder.toString();
        }
    }

    public static ByteBuffer readImage(String path) throws IOException {
        try (var channel = Channels.newChannel(getResource(path))) {
            var buffer = memCalloc(1024 * 2);

            while(channel.read(buffer) != -1) {
                if (buffer.remaining() == 0) {
                    buffer = memRealloc(buffer, buffer.capacity() * 2);
                }
            }

            return buffer.flip();
        }
    }
}
