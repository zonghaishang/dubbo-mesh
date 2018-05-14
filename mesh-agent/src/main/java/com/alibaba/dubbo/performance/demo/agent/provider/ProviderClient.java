package com.alibaba.dubbo.performance.demo.agent.provider;

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

    IntObjectMap<ChannelHandlerContext> channelHandlerContextMap = new IntObjectHashMap(100);
    ChannelFuture channelFuture;
    public static final int HEADER_SIZE = 16;
    String dubboHost = IpHelper.getHostIp();
    int dubboPort = Integer.valueOf(System.getProperty(Constants.DUBBO_PROTOCOL_PORT));

    public void initProviderClient(ChannelHandlerContext channelHandlerContext) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(1024))
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

                        int id = (int) byteBuf.readLong();

                        int dataLength = byteBuf.readInt();
                        if (status != 20) {
                            log.error("非20结果集");
                            byteBuf.skipBytes(dataLength);
                            return;
                        }

                        if (byteBuf.readableBytes() < dataLength) {
                            byteBuf.resetReaderIndex();
                            return;
                        }

                        ByteBuf res = ctx.alloc().directBuffer();
                        //跳过了双引号，因此长度-3
                        res.writeInt(dataLength-3);

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
        log.info("开始创建到dubbo的链接,host:{},ip:{}",dubboHost,dubboPort);
        channelFuture = bootstrap.group(channelHandlerContext.channel().eventLoop()).connect(new InetSocketAddress(dubboHost, dubboPort));
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
