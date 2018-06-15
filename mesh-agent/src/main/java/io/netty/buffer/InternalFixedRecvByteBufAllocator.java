package io.netty.buffer;

import io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator;
import io.netty.util.internal.PlatformDependent;

/**
 * @author yiji@apache.org
 */
public class InternalFixedRecvByteBufAllocator extends DefaultMaxMessagesRecvByteBufAllocator {

    private final int bufferSize;

    private final class HandleImpl extends MaxMessageHandle {
        private final int bufferSize;

        public HandleImpl(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        @Override
        public int guess() {
            return bufferSize;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            if (PlatformDependent.hasUnsafe()) {
                return alloc.directBuffer(guess());
            }
            return alloc.heapBuffer(guess());

        }
    }

    /**
     * Creates a new predictor that always returns the same prediction of
     * the specified buffer size.
     */
    public InternalFixedRecvByteBufAllocator(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(
                    "bufferSize must greater than 0: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new InternalFixedRecvByteBufAllocator.HandleImpl(bufferSize);
    }

    @Override
    public InternalFixedRecvByteBufAllocator respectMaybeMoreData(boolean respectMaybeMoreData) {
        super.respectMaybeMoreData(respectMaybeMoreData);
        return this;
    }

}
