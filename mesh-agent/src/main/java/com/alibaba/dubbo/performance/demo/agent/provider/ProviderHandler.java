package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProviderHandler.class);
    private static ProviderClient providerClient;
    private static int HEADER_LENGTH = 4;

    protected static final byte[] STR_START_BYTES = ("\"2.0.1\"\n" +
            "\"com.alibaba.dubbo.performance.demo.provider.IHelloService\"\n" +
            "null\n" +
            "\"hash\"\n" +
            "\"Ljava/lang/String;\"\n\"").getBytes();
    protected static final byte[] STR_END_BYTES   = "\"\n{\"path\":\"com.alibaba.dubbo.performance.demo.provider.IHelloService\"}\n".getBytes();

    private static final int STR_LENGTH = 173;

    private byte[] header = new byte[] {-38, -69, -58, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        if (providerClient == null) {
            log.info("init providerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            providerClient = new ProviderClient();
            providerClient.initProviderClient(ctx);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf)msg;
        try {
            while (byteBuf.isReadable()){
                int allDataLength = byteBuf.readInt();
                //已经读了一个int，因此-4
                if(byteBuf.readableBytes() < (allDataLength - 4)){
                    byteBuf.resetReaderIndex();
                    return;
                }

                //dubboRequest encode
                int parameterLength = allDataLength - 4;
                byteBuf.markReaderIndex();
                byteBuf.skipBytes(parameterLength);
                int id = byteBuf.readInt();
                byteBuf.resetReaderIndex();

                Bytes.long2bytes(id, header, 4);
                Bytes.int2bytes(parameterLength + STR_LENGTH, header, 12);

                ByteBuf dubboRequest = ctx.alloc().directBuffer();
                dubboRequest.writeBytes(header)
                        .writeBytes(STR_START_BYTES)
                        .writeBytes(byteBuf,byteBuf.readerIndex(),parameterLength)
                        .writeBytes(STR_END_BYTES);
                byteBuf.skipBytes(parameterLength + 4);
                providerClient.send(ctx,dubboRequest,id);
            }
        }finally {
            ReferenceCountUtil.release(msg);
        }
    }



}
