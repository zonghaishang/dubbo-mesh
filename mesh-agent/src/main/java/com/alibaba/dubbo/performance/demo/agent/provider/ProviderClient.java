package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.registry.IpHelper;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
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

/**
 * @author 景竹 2018/5/13
 */
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    IntObjectMap<ChannelHandlerContext> channelHandlerContextMap = new IntObjectHashMap(128);
    ChannelFuture channelFuture;
    public static final int HEADER_SIZE = 16;
    String dubboHost = IpHelper.getHostIp();
    int dubboPort = Integer.valueOf(System.getProperty(Constants.DUBBO_PROTOCOL_PORT));
    ByteBuf res;

    public void initProviderClient(ChannelHandlerContext channelHandlerContext) {
        if(res == null){
            res = channelHandlerContext.alloc().directBuffer();
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Constants.FIXED_RECV_BYTEBUF_ALLOCATOR))
                .option(ChannelOption.SO_SNDBUF, Constants.SEND_BUFFER_SIZE)
                .option(ChannelOption.SO_RCVBUF, Constants.RECEIVE_BUFFER_SIZE)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        try{
                            while (byteBuf.isReadable()){
                                if (byteBuf.readableBytes() <= HEADER_SIZE) {
                                    return;
                                }
                                byteBuf.markReaderIndex();
                                byteBuf.readerIndex(byteBuf.readerIndex()+4);
                                //byte status = byteBuf.readByte();

                                int id = (int) byteBuf.readLong();

                                int dataLength = byteBuf.readInt();
                                /*if (status != 20) {
                                    log.error("非20结果集");
                                    byteBuf.skipBytes(dataLength);
                                    return;
                                }*/

                                if (byteBuf.readableBytes() < dataLength) {
                                    byteBuf.resetReaderIndex();
                                    return;
                                }


                                //跳过了双引号，因此长度-3
                                res.clear();
                                res.writeInt(dataLength-3);

                                byteBuf.readerIndex(byteBuf.readerIndex()+2);
                                res.writeBytes(byteBuf, byteBuf.readerIndex(), dataLength - 3);

                                byteBuf.readerIndex(byteBuf.readerIndex() + dataLength - 2);

                                //System.out.println("id" + id);
                                //System.out.println("dataLength" + dataLength);
                                res.writeInt(id);
                                ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                if(client != null){
                                    client.writeAndFlush(res.retain());
                                }
                            }
                        }finally {
                            ReferenceCountUtil.release(msg);
                        }
                    }
                });
        log.info("开始创建到dubbo的链接,host:{},ip:{}",dubboHost,dubboPort);
        channelFuture = bootstrap.group(channelHandlerContext.channel().eventLoop()).connect(new InetSocketAddress(dubboHost, dubboPort));
    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, int id) {
        channelHandlerContextMap.put(id, channelHandlerContext);
        if (channelFuture != null && channelFuture.isDone()) {
            channelFuture.channel().writeAndFlush(byteBuf);
        } else if(channelFuture != null){
            channelFuture.addListener(r -> channelFuture.channel().writeAndFlush(byteBuf));
        } else {
            ReferenceCountUtil.release(byteBuf);
            ByteBuf res = channelHandlerContext.alloc().directBuffer(20);
            res.writeInt(1);
            res.writeByte(1);
            res.writeInt(id);
        }
    }
}
