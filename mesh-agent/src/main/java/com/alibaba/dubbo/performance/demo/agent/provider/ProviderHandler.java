package com.alibaba.dubbo.performance.demo.agent.provider;

import com.sun.org.apache.xpath.internal.operations.String;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx){

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        System.out.println(msg);
    }
}
