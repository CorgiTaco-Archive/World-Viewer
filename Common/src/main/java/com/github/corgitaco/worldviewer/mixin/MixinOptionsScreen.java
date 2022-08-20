package com.github.corgitaco.worldviewer.mixin;

import com.github.corgitaco.worldviewer.client.WorldScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {

    protected MixinOptionsScreen(Component $$0) {
        super($$0);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addWorldViewButton(CallbackInfo ci) {
        if (minecraft.isLocalServer() && minecraft.getSingleplayerServer() != null) {
            this.addRenderableWidget(new Button(this.width / 2 - 155, this.height / 6 + 144 - 6, 150, 20, new TranslatableComponent("World Viewer"), (p_96268_) -> {
                this.minecraft.setScreen(new WorldScreen(new TextComponent("")));
            }));
        }
    }
}
