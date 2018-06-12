package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author yiji@apache.org
 */
public class InternalReadTimeoutHandler extends ReadIdleStateHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalReadTimeoutHandler.class);

    private boolean closed;

    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
    private static int HeaderLength = 90;
    private static int zero = (int) '0';

    /**
     * Creates a new instance.
     *
     * @param timeoutSeconds read timeout in seconds
     */
    public InternalReadTimeoutHandler(int timeoutSeconds) {
        this(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param timeout read timeout
     * @param unit    the {@link TimeUnit} of {@code timeout}
     */
    public InternalReadTimeoutHandler(long timeout, TimeUnit unit) {
        super(timeout, unit);
    }

    @Override
    protected final void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        log.warn("close channel because of idel.");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("exception caughted! ", cause);
        ctx.close();
    }
}
