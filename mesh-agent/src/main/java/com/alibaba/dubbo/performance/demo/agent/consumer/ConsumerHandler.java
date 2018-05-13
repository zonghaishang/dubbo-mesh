package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ConsumerHandler.class);

    ConsumerClient consumerClient;
    private static byte[] CONTENT_LENGTH = "Content-Length: ".getBytes();
    private static byte[] PARAMETER = "parameter=".getBytes();
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/json\r\n" +
            "Connection: keep-alive\r\n" +
            "Content-Length: ").getBytes();
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
        if(byteBuf.readableBytes() < HTTP_HEAD.length){
            return;
        }
        int bytes = byteBuf.readableBytes();
        byteBuf.readBytes(bytesContent, 0, bytes);

        int i = 0;
        int contentLength = 0;
        for (; i < bytes; ) {
            if (bytesContent[i++] == '\r' && bytesContent[i++] == '\n') {
                if (contentLength == 0 && match(bytesContent, i, CONTENT_LENGTH)) {
                    for (i += CONTENT_LENGTH.length; Character.isDigit(bytesContent[i]); i++) {
                        contentLength = contentLength * 10 + bytesContent[i] - '0';
                    }
                } else if (bytesContent[i++] == '\r' && bytesContent[i++] == '\n') {
                    break; // match body
                }
            }
        }

        if (bytes - i != contentLength) {
            byteBuf.resetReaderIndex();
            return;
        }
        ByteBuf msgToSend = ctx.alloc().buffer();
        int dataLengthIndex = msgToSend.writerIndex();
        //数据总长度
        msgToSend.writeInt(0);
        int dataLength = 0;
        for (int start = i, eq = 0; i < bytes; i++) {
            if (bytesContent[i] == '=') {
                eq = i;
            }
            if (bytesContent[i + 1] == '&' || i == bytes - 1) {
                if (match(bytesContent, start, PARAMETER)) {
                    //数据长度+保存数据长度的int
                    dataLength += i - eq + 4;
                    msgToSend.writeInt(i - eq);
                    msgToSend.writeBytes(bytesContent,eq + 1,i - eq);
                    break;
                }
                start = i + 2;
            }
        }
        int nowWriteIndex = msgToSend.writerIndex();
        //把总长度写进去
        msgToSend.writerIndex(dataLengthIndex);
        msgToSend.writeInt(dataLength);
        msgToSend.writerIndex(nowWriteIndex);

        consumerClient.send(ctx, msgToSend);
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
