package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Instantiable;
import lombok.Getter;
import org.joml.Vector2f;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public final class InstantiableEntity implements Instantiable {
    private final Vector2f translation = new Vector2f();

    private final int texture;

    {
        var random = ThreadLocalRandom.current();

        translation.x = random.nextFloat() - random.nextFloat();
        translation.y = random.nextFloat() - random.nextFloat();

        texture = random.nextInt(2);
    }
}
