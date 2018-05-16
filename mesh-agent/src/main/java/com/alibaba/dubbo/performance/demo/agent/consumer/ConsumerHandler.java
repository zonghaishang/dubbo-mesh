package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ByteProcessor;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ConsumerHandler.class);

    private static ConsumerClient consumerClient;
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static int zero = (int)'0';

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (consumerClient == null) {
            log.info("init consumerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            consumerClient = new ConsumerClient();
            consumerClient.initConsumerClient(ctx);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        try{
            while (byteBuf.isReadable()){
                if(byteBuf.readableBytes() < HTTP_HEAD.length){
                    return;
                }
                int totalLength = byteBuf.readableBytes();

                int contentLength = 0;
                byteBuf.skipBytes(33);
                int header_length=143;
                for(;;){
                    byte b = byteBuf.readByte();
                    if(Character.isDigit(b)){
                        contentLength = contentLength * 10 + b - zero;
                        header_length++;
                    }else {
                        break;
                    }
                }
                //前面的接口什么的都是固定的，长度136
                int paramStart = header_length + 136;
                int paramLength = totalLength - paramStart;
                ByteBuf msgToSend = ctx.alloc().directBuffer(paramLength + 8);
                //数据总长度
                msgToSend.writeInt(paramLength + 4);
                msgToSend.writeInt(paramLength );
                msgToSend.writeBytes(byteBuf.slice(paramStart,paramLength));

                byteBuf.skipBytes(totalLength - byteBuf.readerIndex());

                consumerClient.send(ctx, msgToSend);
            }
        }finally {
            ReferenceCountUtil.release(msg);
        }
    }

}
