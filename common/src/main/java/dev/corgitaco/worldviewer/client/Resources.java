package dev.corgitaco.worldviewer.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
}
