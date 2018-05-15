package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import com.alibaba.dubbo.performance.demo.agent.util.WeightUtil;
import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
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
    IntObjectMap<ChannelFuture> channelFutureMap = new IntObjectHashMap<>(128);
    IntObjectMap<ChannelHandlerContext> channelHandlerContextMap = new IntObjectHashMap(128);
    int id = 0;

    /*private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/json\r\n" +
            "Connection: keep-alive\r\n" +
            "Content-Length: ").getBytes();*/
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
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
            log.info("注册中心找到的endpoint host:{},port:{}",endpoint.getHost(),endpoint.getPort());
            Bootstrap bootstrap = new Bootstrap();
            channelFutureMap.put(endpoint.getPort(),bootstrap.channel(NioSocketChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Constants.RECEIVE_BUFFER_SIZE))
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg){
                            ByteBuf byteBuf = (ByteBuf) msg;
                            try{
                                while (byteBuf.readableBytes() > HeaderLength){
                                    int dataLength = byteBuf.readInt();
                                    if(byteBuf.readableBytes() < dataLength + 4){
                                        byteBuf.resetReaderIndex();
                                        return;
                                    }
                                    //结果集
                                    ByteBuf resByteBuf = ctx.alloc().directBuffer();
                                    resByteBuf.writeBytes(HTTP_HEAD);
                                    if (dataLength < 10) {
                                        resByteBuf.writeByte('0' + dataLength);
                                    } else {
                                        resByteBuf.writeByte('0' + dataLength / 10);
                                        resByteBuf.writeByte('0' + dataLength % 10);
                                    }
                                    resByteBuf.writeBytes(RN_2);

                                    resByteBuf.writeBytes(byteBuf,byteBuf.readerIndex(),dataLength);
                                    byteBuf.skipBytes(dataLength);

                                    int id = byteBuf.readInt();
                                    ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                    if(client != null){
                                        client.writeAndFlush(resByteBuf);
                                    }else {
                                        resByteBuf.release();
                                    }
                                }
                            }finally {
                                byteBuf.release();
                            }
                        }
                    }).group(channelHandlerContext.channel().eventLoop())
                    .connect(
                            new InetSocketAddress(endpoint.getHost(), endpoint.getPort())));
            log.info("创建到provider agent的连接成功,hots:{},port:{}",endpoint.getHost(),endpoint.getPort());
            log.info("channelFutureMap key有：{}",JSON.toJSONString(channelFutureMap.keySet()));
        }

    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf){
        byteBuf.writeInt(id);
        channelHandlerContextMap.put(id++,channelHandlerContext);
        int port  = WeightUtil.getRandom();
        ChannelFuture channelFuture = getChannel(port);
        if(channelFuture!=null && channelFuture.isDone()){
            channelFuture.channel().writeAndFlush(byteBuf);
        }else if(channelFuture!=null){
            channelFuture.addListener(r -> channelFuture.channel().writeAndFlush(byteBuf));
        }else {
            byteBuf.release();
            ByteBuf res = channelHandlerContext.alloc().buffer();
            res.writeBytes(HTTP_HEAD);
            res.writeByte('0'+ 0);
            res.writeBytes(RN_2);
            channelHandlerContext.writeAndFlush(res);
        }

    }

    public ChannelFuture getChannel(int port){
        return channelFutureMap.get(port);
    }
}
