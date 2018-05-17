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

    private static ThreadLocal<ConsumerClient> threadLocal = new ThreadLocal<>();
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static int zero = (int)'0';

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (threadLocal.get() == null) {
            log.info("init consumerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            ConsumerClient consumerClient = new ConsumerClient();
            consumerClient.initConsumerClient(ctx);
            threadLocal.set(consumerClient);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;

        try{
            while (byteBuf.isReadable()){
                int totalLength  = byteBuf.readableBytes();
                if(totalLength < HTTP_HEAD.length){
                    return;
                }
                int contentLength = 0;
                byteBuf.skipBytes(33);
                int header_length=143;
                for(;;){
                    byte b = byteBuf.readByte();
                    if(b != '\r'){
                        contentLength = contentLength * 10 + b - zero;
                        header_length++;
                    }else {
                        break;
                    }
                }
                if(totalLength - header_length < contentLength){
                    byteBuf.resetReaderIndex();
                    return;
                }

                //前面的接口什么的都是固定的，长度136
                int paramStart = header_length + 136;
                int paramLength = contentLength - 136;
                /*ByteBuf msgToSend = ctx.alloc().directBuffer(paramLength + 8);
                //数据总长度
                msgToSend.writeInt(paramLength + 4);
                msgToSend.writeBytes(byteBuf.slice(paramStart,paramLength));*/

                byteBuf.markWriterIndex();
                byteBuf.writerIndex(paramStart - 8);
                byteBuf.writeInt(paramLength + 4);
                byteBuf.writeInt(0);
                byteBuf.resetWriterIndex();


                byteBuf.skipBytes(header_length + contentLength - byteBuf.readerIndex());
                threadLocal.get().send(ctx, byteBuf.slice(paramStart - 8,paramLength+8).retain());
            }
        }finally {
            ReferenceCountUtil.release(msg);
        }
    }

}
