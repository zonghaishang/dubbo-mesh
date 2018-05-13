package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProviderHandler.class);
    ProviderClient providerClient;
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
            log.info("init consumerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            providerClient = new ProviderClient();
            providerClient.initConsumerClient(ctx);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf)msg;
        int allDataLength = byteBuf.readInt();
        //数据的长度+id长度
        if(byteBuf.readableBytes() < (allDataLength + HEADER_LENGTH)){
            byteBuf.resetReaderIndex();
            return;
        }

        ByteBuf dubboRequest = ctx.alloc().directBuffer();

        //dubboRequest encode
        int parameterLength = byteBuf.readInt();

        byteBuf.markReaderIndex();
        byteBuf.skipBytes(parameterLength);
        int id = byteBuf.readInt();
        byteBuf.resetReaderIndex();

        Bytes.long2bytes(id, header, 4);
        Bytes.int2bytes(parameterLength + STR_LENGTH, header, 12);

        dubboRequest.writeBytes(header)
                .writeBytes(STR_START_BYTES)
                .writeBytes(byteBuf,byteBuf.readerIndex(),parameterLength)
                .writeBytes(STR_END_BYTES);
        providerClient.send(ctx,dubboRequest,id);
        byteBuf.skipBytes(parameterLength + 4);
        //dubbo encode end

        /*ByteBuf res = ctx.alloc().directBuffer();
        int parameterLength = byteBuf.readInt();
        byte[] bytes= new byte[parameterLength];
        byteBuf.readBytes(bytes);
        System.out.println(new String(bytes));
        int id = byteBuf.readInt();
        System.out.println(id);
        //数据长度
        res.writeInt(4);
        res.writeBytes("1234".getBytes());
        res.writeInt(id);
        ctx.writeAndFlush(res);*/
    }



}
