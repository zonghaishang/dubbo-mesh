package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import io.netty.buffer.ByteBuf;
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
    protected static final byte[] STR_END_BYTES   = "\"\n{\"path\":\"com.alibaba.dubbo.performance.demo.provider.IHelloService\"}\n".getBytes();

    private static final int STR_LENGTH = 173;

    private byte[] header = new byte[] {-38, -69, -58, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ByteBuf dubboRequest;

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        dubboRequest = ctx.alloc().directBuffer(3000).writeBytes(header).writeBytes(STR_START_BYTES);
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
                Bytes.long2bytes(id, header, 4);
                Bytes.int2bytes(parameterLength + STR_LENGTH, header, 12);

                //dubboRequest已经写好了部分东西了，直接用
                dubboRequest.readerIndex(0);
                dubboRequest.writerIndex(0);
                //写入头
                dubboRequest.writeBytes(header);
                //跳到写param的地方
                dubboRequest.writerIndex(header.length + STR_START_BYTES.length);
                //写param
                dubboRequest.writeBytes(byteBuf,byteBuf.readerIndex(),parameterLength)
                        .writeBytes(STR_END_BYTES);
                byteBuf.readerIndex(byteBuf.readerIndex() + parameterLength);
                threadLocal.get().send(ctx,dubboRequest.retain(),id);
            }
        }finally {
            ReferenceCountUtil.release(msg);
        }
    }



}
