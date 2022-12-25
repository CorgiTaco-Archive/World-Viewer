package com.corgitaco.worldviewer.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

public class WVContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    public WVContainerObjectSelectionList(int width, int height, int listTop, int listBottom, int entrySize) {
        super(Minecraft.getInstance(), width, height, listTop, listBottom, entrySize);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 5;
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    // Fixes an issue in vanilla lists where entries would render above their bounds.
    @Override
    protected int getRowTop(int index) {
        int rowTop = super.getRowTop(index);
        if (rowTop < this.y0) {
            return Integer.MAX_VALUE;
        }
        return rowTop;
    }

    // Fixes an issue in vanilla lists where entries would render below their bounds.
    @Override
    public int getRowBottom(int index) {
        int rowBottom = super.getRowBottom(index);
        if (rowBottom > this.y1) {
            return Integer.MIN_VALUE;
        }
        return rowBottom;
    }

}
