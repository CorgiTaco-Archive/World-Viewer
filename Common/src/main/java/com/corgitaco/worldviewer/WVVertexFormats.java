package com.corgitaco.worldviewer;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public class WVVertexFormats {

    public static final VertexFormatElement TEX_INDEX = new VertexFormatElement(2, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

    public static final VertexFormat POSITION_TEX_INDEX = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder().put("Position", DefaultVertexFormat. ELEMENT_POSITION).put("UV0", DefaultVertexFormat.ELEMENT_UV0).put("Tex_Index", TEX_INDEX).build());

}
