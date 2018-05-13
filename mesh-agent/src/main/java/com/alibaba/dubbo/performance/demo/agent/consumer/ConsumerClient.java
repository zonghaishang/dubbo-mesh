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
    IntObjectMap<ChannelFuture> channelFutureMap = new IntObjectHashMap<>(50);
    IntObjectMap<ChannelHandlerContext> channelHandlerContextMap = new IntObjectHashMap(400);
    int id = 0;

    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/json\r\n" +
            "Connection: keep-alive\r\n" +
            "Content-Length: ").getBytes();
    private static byte[] RN = "\r\n".getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
    private static byte[] CONTENT_LENGTH = "th: ".getBytes();
    private static int HeaderLength = 8;


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
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg){
                            ByteBuf byteBuf = (ByteBuf) msg;

                            while (byteBuf.readableBytes() > HeaderLength){
                                int dataLength = byteBuf.readInt();
                                if(byteBuf.readableBytes() < dataLength){
                                    byteBuf.resetReaderIndex();
                                    return;
                                }
                                int id = byteBuf.readInt();
                                int resLength = dataLength - 4;
                                ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                ByteBuf resByteBuf = ctx.alloc().directBuffer();
                                resByteBuf.writeBytes(HTTP_HEAD);
                                resByteBuf.writeInt(resLength);
                                resByteBuf.writeBytes(RN_2);
                                resByteBuf.writeBytes(byteBuf.slice(byteBuf.readerIndex(),resLength));
                                client.writeAndFlush(resByteBuf);
                                byteBuf.skipBytes(resLength);
                            }
                        }
                    });
            bootstrap.group(channelHandlerContext.channel().eventLoop());
            ChannelFuture connectFuture = null;
            try {
                connectFuture = bootstrap.connect(
                        new InetSocketAddress(endpoint.getHost(), endpoint.getPort()));
            } catch (Exception e) {
                log.error("创建到provider agent的连接失败",e);
            }
            channelFutureMap.put(endpoint.getPort(),connectFuture);
            log.error("创建到provider agent的连接成功");
        }

    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf){
        channelHandlerContextMap.put(id++,channelHandlerContext);
        ChannelFuture channelFuture = getChannel(WeightUtil.getRandom());
        if(channelFuture!=null && channelFuture.isDone() && channelFuture.channel().isWritable()){
            channelFuture.channel().writeAndFlush(byteBuf);
        }else if(channelFuture!=null){
            channelFuture.addListener(r -> channelFuture.channel().writeAndFlush(byteBuf));
        }else {
            ByteBuf res = channelHandlerContext.alloc().buffer();
            res.writeBytes(HTTP_HEAD);
            res.writeInt(0);
            res.writeBytes(RN_2);
            channelHandlerContext.writeAndFlush(res);
        }

    }

    public ChannelFuture getChannel(int port){
        return channelFutureMap.get(port);
    }
}
