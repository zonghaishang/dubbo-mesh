package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.registry.IpHelper;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import com.alibaba.dubbo.performance.demo.agent.util.InternalIntObjectHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
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

    InternalIntObjectHashMap<ChannelHandlerContext> channelHandlerContextMap = new InternalIntObjectHashMap<>(524288);
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

    public static final byte FLAG_TWOWAY = (byte) 0x40;
    public static final byte FLAG_EVENT = (byte) 0x20;

    private static final ByteBuf RN_2_BUFF = Unpooled.wrappedBuffer(RN_2);
    private static int zero = (int) '0';
    private int sendCount = 0;

    SpscLinkedQueue<ByteBuf> readQueue = new SpscLinkedQueue<ByteBuf>();

    public void initProviderClient(ChannelHandlerContext channelHandlerContext) {
        if (res == null) {
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
                        try {
                            while (byteBuf.readableBytes() >= HEADER_SIZE) {
                                byteBuf.markReaderIndex();

                                int readerIndex = byteBuf.readerIndex();
                                byte status = byteBuf.getByte(byteBuf.readerIndex() + 3);
                                //直接跳到dubbo response读ID的位置
                                byteBuf.readerIndex(byteBuf.readerIndex() + 4);
                                //byte status = byteBuf.readByte();

                                int id = (int) byteBuf.readLong();
                                int dataLength = byteBuf.readInt();

                                // 如果是事件
                                if ((byteBuf.getByte(readerIndex + 2) & FLAG_EVENT) != 0) {
                                    byteBuf.setByte(readerIndex + 2, FLAG_TWOWAY | FLAG_EVENT | 6);
                                    byteBuf.setByte(readerIndex + 3, 20);
                                    ByteBuf hearbeat = byteBuf.slice(readerIndex, 16 + 5).retain();

                                    channelFuture.channel().writeAndFlush(hearbeat, channelFuture.channel().voidPromise());

                                    byteBuf.readerIndex(byteBuf.readerIndex() + dataLength);
                                    log.error("收到心跳并响应");
                                    return;
                                }

                                /*byte status = byteBuf.getByte(3);*/
                                if (status != 20 && status != 100) {
                                    log.error("非20结果集, status:" + status);

                                    byteBuf.readerIndex(byteBuf.readerIndex() + dataLength);
                                    return;
                                }

                                if (byteBuf.readableBytes() < dataLength) {
                                    byteBuf.resetReaderIndex();
                                    return;
                                }

                                //dubbo的response是json，跳过双引号（开头2个，结尾1个），因此长度-3
                                int httpDataLength = dataLength - 3;

                                if (status == 100) {
                                    log.error("dubbo线程池已满,id{}", id);
                                    // 如果线程池满，直接长度设置为1
                                    httpDataLength = 1;
                                }

//                                res = resps[requestIndex++ % Constants.PROVIDER_BACK_BATCH_SIZE * 6];
                                res.clear();
                                ////消息的格式为： 4byte（int长度）+ 4byte（int id）+ provider agent完整拼接好的http response
                                res.setInt(0, id);
                                res.setInt(4, httpDataLength);

                                //复用res，http头已经写好，加上id和长度，则还需要跳过8byte
                                //res.writerIndex(HTTP_HEAD.length + 8);

                                //content-length 长度不定，小于10的数据占1byte，否则2byte
                                if (httpDataLength < 10) {
                                    res.setByte(HTTP_HEAD.length + 8, zero + httpDataLength);
                                    res.writerIndex(HTTP_HEAD.length + 9);
                                } else {
                                    res.setByte(HTTP_HEAD.length + 8, zero + httpDataLength / 10);
                                    res.setByte(HTTP_HEAD.length + 9, zero + httpDataLength % 10);
                                    res.writerIndex(HTTP_HEAD.length + 10);
                                }
                                //写入\r\n
                                /*res.writeBytes(RN_2);
                                if (status == 100) {
                                    // 如果线程池满，body直接写0
                                    res.writeByte('0');
                                } else {
                                    //跳过json前面2个引号，因此+2.还要跳过屁股一个引号，因此httpDataLength = dataLength - 3
                                    res.writeBytes(byteBuf, byteBuf.readerIndex() + 2, httpDataLength);
                                }*/

                                ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                if (client != null) {
                                    client.write(res.retain(), client.voidPromise());
                                    client.write(RN_2_BUFF.retain(), client.voidPromise());
                                    if (status == 100) {
                                        client.writeAndFlush('0', client.voidPromise());
                                    } else {
                                        client.writeAndFlush(byteBuf.slice(byteBuf.readerIndex() + 2, httpDataLength).retain(), client.voidPromise());
                                    }
                                    //client.writeAndFlush(res.retain(), client.voidPromise());
                                }
                                byteBuf.readerIndex(byteBuf.readerIndex() + dataLength);
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
                        ch.write(buf, ch.voidPromise());
                    }

                    // Must flush at least once, even if there were no writes.
                    if (flush)
                        ch.flush();
                }
            }
        });
    }

    public void send(ChannelHandlerContext channelHandlerContext, int id, ByteBuf head, ByteBuf start, ByteBuf param, ByteBuf end) {
        channelHandlerContextMap.put(id, channelHandlerContext);
        if (channelFuture != null && channelFuture.isSuccess()) {
            Channel channel = channelFuture.channel();
            if (++sendCount < Constants.PROVIDER_BATCH_SIZE) {
                channel.write(head.retain(), channel.voidPromise());
                channel.write(start.retain(), channel.voidPromise());
                channel.write(param.retain(), channel.voidPromise());
                channel.write(end.retain(), channel.voidPromise());
            } else {
                channel.write(head.retain(), channel.voidPromise());
                channel.write(start.retain(), channel.voidPromise());
                channel.write(param.retain(), channel.voidPromise());
                channel.writeAndFlush(end.retain(), channel.voidPromise());
                sendCount = 0;
            }
        } else {
            //ReferenceCountUtil.release(param);
            //在handler中已经release了，这里不需要再释放
            res.clear();
            res.writeInt(id);
            res.writeInt(1);
            res.writerIndex(HTTP_HEAD.length + 8);
            res.writeByte(zero + 1);
            res.writeBytes(RN_2);
            res.writeByte(1);
            channelHandlerContext.writeAndFlush(res.retain(), channelHandlerContext.channel().voidPromise());
        }
    }
}
