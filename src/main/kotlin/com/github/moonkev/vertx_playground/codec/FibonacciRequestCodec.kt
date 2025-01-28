package com.github.moonkev.vertx_playground.codec

import com.github.moonkev.math.v1.FibonacciRequest
import io.vertx.core.buffer.Buffer
import io.vertx.core.buffer.impl.BufferImpl
import io.vertx.core.eventbus.MessageCodec

class FibonacciRequestCodec : MessageCodec<FibonacciRequest, FibonacciRequest> {

    companion object {
       private const val CODEC_NAME = "FibonacciRequestCode"
    }

    override fun name(): String {
        return CODEC_NAME
    }

    override fun encodeToWire(buffer: Buffer, message: FibonacciRequest) {
        val bytes = message.toByteArray()
        buffer.appendInt(bytes.size)
        buffer.appendBytes(bytes)
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): FibonacciRequest? {
        val length = buffer.getInt(pos)
        val messagePos = pos + 4
        val bytes = buffer.getBytes(messagePos, messagePos + length)
        return FibonacciRequest.parseFrom(bytes)
    }

    override fun transform(request: FibonacciRequest): FibonacciRequest {
        return request
    }

    override fun systemCodecID(): Byte {
        return -1
    }
}