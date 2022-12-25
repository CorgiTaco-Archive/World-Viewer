package com.corgitaco.worldviewer.client.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.util.FastColor;

import java.util.List;

public class WidgetList extends WVContainerObjectSelectionList<WidgetList.Entry> {

    public WidgetList(List<AbstractWidget> widgets, int width, int height, int y0, int y1, int itemHeight) {
        super(width, height, y0, y1, itemHeight);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("Must have at least 1 widget.");
        }

        for (AbstractWidget widget : widgets) {
            this.addEntry(new Entry(widget));
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        GuiComponent.fill(poseStack, this.x0, this.y0, this.x1, this.y1, FastColor.ARGB32.color(255,0,0,0));
        int x = this.getRowLeft();
        int y = this.y0 + 4 - (int)this.getScrollAmount();
        super.render(poseStack, mouseX, mouseY, partialTick);
        super.renderList(poseStack, x, y, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderList(PoseStack pPoseStack, int pX, int pY, int pMouseX, int pMouseY, float pPartialTick) {
    }

    @Override
    protected int addEntry(Entry $$0) {
        return super.addEntry($$0);
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        private final AbstractWidget widget;

        public Entry(AbstractWidget text) {
            this.widget = text;
        }

        @Override
        public void render(PoseStack pPoseStack, int pIndex, int pTop, int pLeft, int rowWidth, int pHeight, int pMouseX, int pMouseY, boolean pIsMouseOver, float pPartialTick) {
            this.widget.x = pLeft;
            this.widget.y = pTop;
            this.widget.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.widget);
        }
    }
}