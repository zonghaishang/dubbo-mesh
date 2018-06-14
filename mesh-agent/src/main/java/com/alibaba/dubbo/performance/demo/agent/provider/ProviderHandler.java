package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProviderHandler.class);
    private static FastThreadLocal<ProviderClient> threadLocal = new FastThreadLocal<>();;
    private static int HEADER_LENGTH = 4;

    protected static final byte[] STR_START_BYTES = ("\"2.0.1\"\n" +
            "\"com.alibaba.dubbo.performance.demo.provider.IHelloService\"\n" +
            "null\n" +
            "\"hash\"\n" +
            "\"Ljava/lang/String;\"\n\"").getBytes();
    protected static final ByteBuf STR_START_BYTES_BUF = Unpooled.wrappedBuffer(STR_START_BYTES);
    protected static final byte[] STR_END_BYTES   = "\"\n{\"path\":\"com.alibaba.dubbo.performance.demo.provider.IHelloService\"}\n".getBytes();
    protected static final ByteBuf STR_END_BYTES_BUF   = Unpooled.wrappedBuffer(STR_END_BYTES);

    private static final int STR_LENGTH = 173;

    private byte[] header = new byte[] {-38, -69, -58, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private ByteBuf header_buff = Unpooled.wrappedBuffer(header);

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        //为什么要申请这么多块内存？因为合并请求只是write，最后才flush，为了防止bytebuf被覆盖，因此申请BatchSize多块内存空间
        if (threadLocal.get() == null) {
            log.info("init providerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            ProviderClient providerClient = new ProviderClient();
            providerClient.initProviderClient(ctx);
            threadLocal.set(providerClient);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf)msg;
        try {
            while (byteBuf.isReadable()){
                byteBuf.markReaderIndex();
                //接收到的消息格式为： 4byte（总消息长度）+ 4byte（id）+ 具体param消息
                int readableBytes = byteBuf.readableBytes();
                if(readableBytes < 4){
                    byteBuf.resetReaderIndex();
                    return;
                }
                int allDataLength = byteBuf.readInt();
                //已经读了一个int
                if(byteBuf.readableBytes() < allDataLength){
                    byteBuf.resetReaderIndex();
                    return;
                }
                //
                int id = byteBuf.readInt();
                //dubboRequest encode

                //已经读了一个id了，因此-4，剩余为param长度
                int parameterLength = allDataLength - 4;

                //把id、param长度写到dubbo头中
                header_buff.clear();
                header_buff.setLong(4,id);
                header_buff.setInt(12,parameterLength + STR_LENGTH);
                header_buff.writerIndex(16);
                threadLocal.get().send(ctx,id,header_buff,STR_START_BYTES_BUF,byteBuf.slice(byteBuf.readerIndex(),parameterLength),STR_END_BYTES_BUF);
                byteBuf.readerIndex(byteBuf.readerIndex() + parameterLength);
            }
        }finally {
            if(byteBuf.refCnt() != 0){
                ReferenceCountUtil.release(msg);
            }
        }
    }



}
