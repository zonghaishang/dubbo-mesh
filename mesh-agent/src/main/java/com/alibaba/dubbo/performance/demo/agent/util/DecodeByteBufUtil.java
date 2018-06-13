package com.alibaba.dubbo.performance.demo.agent.util;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * @author yiji@apache.org
 */
public class DecodeByteBufUtil {

    private static final FastThreadLocal<CharBuffer> CHAR_BUFFERS0 = new FastThreadLocal<CharBuffer>() {
        @Override
        protected CharBuffer initialValue() throws Exception {
            return CharBuffer.allocate(1024);
        }
    };

    public static String decodeString(ByteBuf src) {
        return decodeString(src, src.readerIndex(), src.readableBytes(), Charset.defaultCharset());
    }

    public static String decodeString(ByteBuf src, int readerIndex, int len, Charset charset) {
        if (len == 0) {
            return StringUtil.EMPTY_STRING;
        }
        final CharsetDecoder decoder = CharsetUtil.decoder(charset);
        final int maxLength = (int) ((double) len * decoder.maxCharsPerByte());
        CharBuffer dst = CHAR_BUFFERS0.get();
        if (dst.length() < maxLength) {
            dst = CharBuffer.allocate(maxLength);
            if (maxLength <= 16 * 1024) {
                CHAR_BUFFERS0.set(dst);
            }
        } else {
            dst.clear();
        }
        if (src.nioBufferCount() == 1) {
            decodeString(decoder, src.nioBuffer(readerIndex, len), dst);
        } else {
            // We use a heap buffer as CharsetDecoder is most likely able to use a fast-path if src and dst buffers
            // are both backed by a byte array.
            ByteBuf buffer = src.alloc().heapBuffer(len);
            try {
                buffer.writeBytes(src, readerIndex, len);
                // Use internalNioBuffer(...) to reduce object creation.
                decodeString(decoder, buffer.internalNioBuffer(buffer.readerIndex(), len), dst);
            } finally {
                // Release the temporary buffer again.
                buffer.release();
            }
        }
        return dst.flip().toString();
    }

    private static void decodeString(CharsetDecoder decoder, ByteBuffer src, CharBuffer dst) {
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
    }

}
