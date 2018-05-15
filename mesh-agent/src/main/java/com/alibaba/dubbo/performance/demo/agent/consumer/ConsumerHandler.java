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
    //private static byte[] CONTENT_LENGTH = "Content-Length: ".getBytes();
    private static byte[] CONTENT_LENGTH = "content-length: ".getBytes();
    private static byte[] PARAMETER = "parameter=".getBytes();
    /*private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/json\r\n" +
            "Connection: keep-alive\r\n" +
            "Content-Length: ").getBytes();*/
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private byte[] bytesContent = new byte[3000];

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

                /*byteBuf.markReaderIndex();
                byteBuf.readBytes(bytesContent,0,totalLength);
                System.out.println(new String(bytesContent));
                byteBuf.resetReaderIndex();*/

                int contentLength = 0;
                byteBuf.skipBytes(33);
                for(;;){
                    byte b = byteBuf.readByte();
                    if(Character.isDigit(b)){
                        contentLength = contentLength * 10 + b - '0';
                    }else {
                        break;
                    }
                }
                int header_length;

                if(contentLength > 99 && contentLength < 999){
                    //3位数
                    header_length = 146;
                }else if(contentLength > 999){
                    //4位数
                    header_length = 147;
                }else {
                    //两位数
                    header_length = 145;
                }

                /*if (totalLength - header_length != contentLength) {
                    byteBuf.resetReaderIndex();
                    return;
                }*/
                //前面的接口什么的都是固定的，长度136
                int paramStart = header_length + 136;
                int paramLength = totalLength - paramStart;
                /*byte[] bytes = new byte[2000];
                byteBuf.readBytes(bytes,paramStart,paramLength);*/
                //System.out.println(new String(bytes));
                //byteBuf.readerIndex(byteBuf.readerIndex()+header_length + 136);
                ByteBuf msgToSend = ctx.alloc().directBuffer(paramLength + 8);
                //数据总长度
                msgToSend.writeInt(paramLength + 4);
                msgToSend.writeInt(paramLength );
                msgToSend.writeBytes(byteBuf,paramStart,paramLength);

                byteBuf.skipBytes(totalLength - byteBuf.readerIndex());

                consumerClient.send(ctx, msgToSend);
            }
        }finally {
            byteBuf.release();
        }
    }

    private boolean match(byte[] bb, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length && i + offset < bb.length; i++) {
            if (bb[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

}
