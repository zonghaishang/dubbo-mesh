package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import com.alibaba.dubbo.performance.demo.agent.util.WeightUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerClient {
    private static final Logger log = LoggerFactory.getLogger(ConsumerClient.class);
    IntObjectMap<ChannelFuture> channelFutureIntObjectMap = new IntObjectHashMap<>(50);
    IntObjectMap<ChannelHandlerContext> channelHandlerContextIntObjectMap = new IntObjectHashMap(400);
    int id = 0;


    public void initConsumerClient(ChannelHandlerContext channelHandlerContext) {
        List<Endpoint> endpoints;
        try {
            endpoints = new EtcdRegistry(System.getProperty(Constants.ETCE))
                    .find(Constants.SERVER_NAME);
        } catch (Exception e) {
            log.error("get etcd fail", e);
            throw new IllegalStateException(e);
        }
        for (Endpoint endpoint : endpoints){
            log.info("endpoint host:{},port:{}",endpoint.getHost(),endpoint.getPort());
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {

                    });
            bootstrap.group(channelHandlerContext.channel().eventLoop());
            ChannelFuture connectFuture = null;
            try {
                connectFuture = bootstrap.connect(
                        new InetSocketAddress(endpoint.getHost(), endpoint.getPort())).sync();
            } catch (InterruptedException e) {
                log.error("创建到provider agent的连接失败",e);
            }
            channelFutureIntObjectMap.put(endpoint.getPort(),connectFuture);
        }

    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf){
        channelHandlerContextIntObjectMap.put(id++,channelHandlerContext);
        ChannelFuture channelFuture = getChannel(WeightUtil.getRandom());
        channelFuture.channel().writeAndFlush(byteBuf);
    }

    public ChannelFuture getChannel(int port){
        return channelFutureIntObjectMap.get(port);
    }
}
