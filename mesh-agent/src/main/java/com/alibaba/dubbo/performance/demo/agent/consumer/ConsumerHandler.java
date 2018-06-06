package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ConsumerHandler.class);

    private static FastThreadLocal<ConsumerClient> threadLocal = new FastThreadLocal<>();
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte r = '\r';
    private static int zero = (int)'0';
    byte[] lengthBytes = new byte[5];

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
                    byteBuf.resetReaderIndex();
                    return;
                }
                int contentLength = 0;
                //直接跳到content-length开始读
                byteBuf.readerIndex(33);

                //由于content-length后面数字长度不定，因此在header_length的基础上增加
                int header_length=143;

                //把整个content-length后面5个byte全读出来，最大长度肯定不超过5
                byteBuf.readBytes(lengthBytes);
                for(int i=0;i<lengthBytes.length;i++){
                    if(lengthBytes[i] != r){
                        //不等于\r则说明还是数字部分，结束会以\r结尾
                        contentLength = contentLength * 10 + lengthBytes[i] - zero;
                        header_length++;
                    }else {
                        break;
                    }
                }
                //判断剩余字节够不够读
                if(totalLength - header_length < contentLength){
                    byteBuf.resetReaderIndex();
                    return;
                }

                //前面的消息体中，前面接口、类型等都是固定的，长度136，直接跳到param开始读
                int paramStart = header_length + 136;
                //整个消息的长度减去前面接口、类型等固定长度，得到param的长度
                int paramLength = contentLength - 136;

                byteBuf.markWriterIndex();
                //直接复用传过来的byteBuf，param往前移动8byte，一个用于记录总长度，一个用于保存ID
                byteBuf.writerIndex(paramStart - 8);
                //数据总长度
                byteBuf.writeInt(paramLength + 4);
                byteBuf.writeInt(0);
                byteBuf.resetWriterIndex();


                byteBuf.readerIndex(byteBuf.writerIndex());

                threadLocal.get().send(ctx, byteBuf.slice(paramStart - 8,paramLength+8).retain());
            }
        }finally {
            ReferenceCountUtil.release(msg);
        }
    }

}
