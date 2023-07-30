package dev.corgitaco.worldviewer.client

import java.nio.ByteBuffer

@JvmInline
value class WrappedByteBuffer(val buffer: ByteBuffer) {

	fun putFloatArray(floats: FloatArray) {
		floats.forEach(buffer::putFloat)
	}

	fun putIntArray(ints: IntArray) {
		ints.forEach(buffer::putInt)
	}
}