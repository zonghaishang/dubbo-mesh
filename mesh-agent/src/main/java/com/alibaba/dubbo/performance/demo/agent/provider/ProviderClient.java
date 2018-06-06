package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.registry.IpHelper;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import com.alibaba.dubbo.performance.demo.agent.util.InternalIntObjectHashMap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.shaded.org.jctools.queues.SpscLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author 景竹 2018/5/13
 */
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    InternalIntObjectHashMap<ChannelHandlerContext> channelHandlerContextMap = new InternalIntObjectHashMap<>(65536);
    ChannelFuture channelFuture;
    public static final int HEADER_SIZE = 16;
    String dubboHost = IpHelper.getHostIp();
    int dubboPort = Integer.valueOf(System.getProperty(Constants.DUBBO_PROTOCOL_PORT));
    ByteBuf res;
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
    private static int zero = (int)'0';

    SpscLinkedQueue<ByteBuf> readQueue = new SpscLinkedQueue<ByteBuf>();

    public void initProviderClient(ChannelHandlerContext channelHandlerContext) {
        if(res == null){
            res = channelHandlerContext.alloc().directBuffer(512).writeInt(0).writeInt(0).writeBytes(HTTP_HEAD);
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
                            while (byteBuf.readableBytes() >= HEADER_SIZE){
                                byteBuf.markReaderIndex();
                                byteBuf.readerIndex(byteBuf.readerIndex() + 4);
                                //byte status = byteBuf.readByte();

                                int id = (int) byteBuf.readLong();
                                int dataLength = byteBuf.readInt();
                                /*byte status = byteBuf.getByte(3);
                                if (status != 20 && status != 100) {
                                    log.error("非20结果集");
                                    byteBuf.readerIndex(byteBuf.readerIndex()+dataLength);
                                    return;
                                }*/

                                if (byteBuf.readableBytes() < dataLength) {
                                    byteBuf.resetReaderIndex();
                                    return;
                                }

                                // 线程池满了
//                                if (status == 100) {
//                                    log.error("dubbo线程池已满");
//                                    res.clear();
//                                    res.writeByte(status);
//                                    byteBuf.readerIndex(byteBuf.writerIndex());
//                                    ChannelHandlerContext client = channelHandlerContextMap.get(id & 1023);
//                                    if (client != null) {
//                                        client.writeAndFlush(res.retain(), client.voidPromise());
//                                    }
//                                    return;
//                                }

                                //跳过了双引号，因此长度-3
                                int httpDataLength = dataLength - 3;
                                res.clear();
                                res.writeInt(id);
                                res.writeInt(httpDataLength);
                                res.writerIndex(HTTP_HEAD.length + 8);

                                if (httpDataLength < 10) {
                                    res.writeByte(zero + httpDataLength);
                                } else {
                                    res.writeByte(zero + httpDataLength / 10);
                                    res.writeByte(zero + httpDataLength % 10);
                                }
                                res.writeBytes(RN_2);
                                //byteBuf.readerIndex(18);
                                res.writeBytes(byteBuf, byteBuf.readerIndex()+2, dataLength-3);

                                byteBuf.readerIndex(byteBuf.readerIndex() + dataLength);

                                //System.out.println("id" + id);
                                //System.out.println("dataLength" + dataLength);
                                //res.writeInt(id);
                                /*int le = res.readableBytes();
                                byte[] bytes = new byte[le];
                                res.readBytes(bytes);
                                System.out.println(new String(bytes));*/

                                ChannelHandlerContext client = channelHandlerContextMap.get(id & Constants.MASK);
                                if(client != null){
                                    //Util.printByteBuf(res.slice(8,res.writerIndex()-8));
                                    client.writeAndFlush(res.retain());
                                }
                            }
                        } finally {
                            ReferenceCountUtil.release(msg);
                        }
                    }
                });
        log.info("开始创建到dubbo的链接,host:{},ip:{}", dubboHost, dubboPort);
        channelFuture = bootstrap.group(channelHandlerContext.channel().eventLoop()).connect(new InetSocketAddress(dubboHost, dubboPort));

        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {

                    Channel ch = channelFuture.channel();
                    ByteBuf buf = null;
                    boolean flush = !readQueue.isEmpty();
                    while ((buf = readQueue.poll()) != null) {
                        if (!ch.isWritable()) {
                            ch.flush();
                        }
                        ch.write(buf);
                    }

                    // Must flush at least once, even if there were no writes.
                    if (flush)
                        ch.flush();
                }
            }
        });
    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, int id) {
        channelHandlerContextMap.put(id & Constants.MASK, channelHandlerContext);
        if (channelFuture != null && channelFuture.isSuccess()) {
            channelFuture.channel().writeAndFlush(byteBuf, channelFuture.channel().voidPromise());
        } else if (channelFuture != null && !channelFuture.channel().isActive()) {
            // 消除大量listener资源消耗
            readQueue.offer(byteBuf);
            // channelFuture.addListener(r -> channelFuture.channel().writeAndFlush(byteBuf, channelFuture.channel().voidPromise()));
        } else {
            ReferenceCountUtil.release(byteBuf);
            ByteBuf res = channelHandlerContext.alloc().directBuffer(20);
            res.writeInt(1);
            res.writeByte(1);
            res.writeInt(id);
            channelHandlerContext.writeAndFlush(res, channelHandlerContext.channel().voidPromise());
        }
    }
}
