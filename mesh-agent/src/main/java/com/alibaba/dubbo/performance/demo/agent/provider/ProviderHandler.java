package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static byte[] RN_2 = "\r\n\n".getBytes();
    //private static byte[] CONTENT_LENGTH = "Content-Length: ".getBytes();
    private static int HEADER_LENGTH = 4;

    @Override
    public void channelActive(ChannelHandlerContext ctx){

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
        ByteBuf res = ctx.alloc().directBuffer();
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
        ctx.writeAndFlush(res);
    }



}
