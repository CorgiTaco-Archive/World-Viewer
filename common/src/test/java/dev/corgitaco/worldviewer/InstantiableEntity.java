package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Instantiable;
import lombok.Data;
import org.joml.Vector2f;

@Data
public final class InstantiableEntity implements Instantiable {
    private final Vector2f translation = new Vector2f();
}
