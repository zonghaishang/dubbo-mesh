package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
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

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        if(consumerClient == null){
            log.info("init consumerClient,ctx:{},thread id:{}",ctx.channel().id(),Thread.currentThread().getId());
            consumerClient = new ConsumerClient();
            consumerClient.initConsumerClient(ctx);
        }
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println(byteBuf.indexOf(0,byteBuf.readableBytes(),CONTENT_LENGTH[0]));
        System.out.println(CONTENT_LENGTH.toString());
        //ctx.writeAndFlush(byteBuf);
        consumerClient.send(ctx,byteBuf);
    }
}
