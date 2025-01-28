package com.github.moonkev.vertx_playground.codec

import com.github.moonkev.math.v1.FibonacciResponse
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

class FibonacciResponseCodec : MessageCodec<FibonacciResponse, FibonacciResponse> {

    companion object {
        private const val CODEC_NAME = "FibonacciResponseCode"
    }

    override fun name(): String {
        return CODEC_NAME
    }

    override fun encodeToWire(buffer: Buffer, message: FibonacciResponse) {
        val bytes = message.toByteArray()
        buffer.appendInt(bytes.size)
        buffer.appendBytes(bytes)
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): FibonacciResponse? {
        val length = buffer.getInt(pos)
        val messagePos = pos + 4
        val bytes = buffer.getBytes(messagePos, messagePos + length)
        return FibonacciResponse.parseFrom(bytes)
    }

    override fun transform(request: FibonacciResponse): FibonacciResponse {
        return request
    }

    override fun systemCodecID(): Byte {
        return -1
    }
}