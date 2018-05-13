package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static byte[] RN_2 = "\r\n\n".getBytes();
    byte[] bytes = new byte[200];
    //private static byte[] CONTENT_LENGTH = "Content-Length: ".getBytes();
    private static byte[] CONTENT_LENGTH = "th: ".getBytes();
    @Override
    public void channelActive(ChannelHandlerContext ctx){

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf)msg;
        byteBuf.readBytes(bytes);
        ByteBuf res = ctx.alloc().directBuffer();
        res.writeInt(11);
        res.writeInt(1);
        res.writeBytes("123".getBytes());
        ctx.writeAndFlush(res);
    }



}
