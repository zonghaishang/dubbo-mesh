package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import com.alibaba.dubbo.performance.demo.agent.registry.IpHelper;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author 景竹 2018/5/13
 */
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    IntObjectMap<ChannelHandlerContext> channelHandlerContextMap = new IntObjectHashMap(400);
    ChannelFuture channelFuture;
    public static final int HEADER_SIZE = 16;

    public void initConsumerClient(ChannelHandlerContext channelHandlerContext) {
        String dubboHost = IpHelper.getHostIp();
        int dubboPort = Integer.valueOf(System.getProperty(Constants.DUBBO_PROTOCOL_PORT));
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(10 * 1024))
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;

                        if (byteBuf.readableBytes() <= HEADER_SIZE) {
                            return;
                        }
                        byteBuf.markReaderIndex();
                        byteBuf.skipBytes(3);
                        byte status = byteBuf.readByte();
                        if (status != 20) {
                            return;
                        }

                        int id = (int) byteBuf.readLong();

                        int dataLength = byteBuf.readInt();


                        if (byteBuf.readableBytes() < dataLength) {
                            byteBuf.resetReaderIndex();
                            return;
                        }

                        ByteBuf res = ctx.alloc().directBuffer();
                        res.writeInt(dataLength);

                        byteBuf.skipBytes(2);
                        res.writeBytes(byteBuf, byteBuf.readerIndex(), dataLength - 3);

                        byteBuf.skipBytes(dataLength - 2);

                        //System.out.println("id" + id);
                        //System.out.println("dataLength" + dataLength);
                        res.writeInt(id);
                        ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                        client.writeAndFlush(res);
                    }
                });
        bootstrap.group(channelHandlerContext.channel().eventLoop());
        try {
            channelFuture = bootstrap.connect(
                    new InetSocketAddress(dubboHost, dubboPort));
        } catch (Exception e) {
            log.error("创建到provider agent的连接失败", e);
        }
        log.error("创建到provider agent的连接成功");
    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, int id) {
        channelHandlerContextMap.put(id, channelHandlerContext);
        if (channelFuture != null && channelFuture.isDone()) {
            channelFuture.channel().writeAndFlush(byteBuf);
        } else {
            channelFuture.addListener(r -> channelFuture.channel().writeAndFlush(byteBuf));
        }
    }
}