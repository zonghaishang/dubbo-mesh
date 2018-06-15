package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.balance.BalanceService;
import com.alibaba.dubbo.performance.demo.agent.balance.BalanceServiceImpl;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import com.alibaba.dubbo.performance.demo.agent.util.InternalIntObjectHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.shaded.org.jctools.queues.SpscLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 景竹 2018/5/12
 * @author yiji@apache.org
 */
public class ConsumerClient {
    private static final Logger log = LoggerFactory.getLogger(ConsumerClient.class);
    //InternalIntObjectHashMap<ChannelFuture> channelFutureMap = new InternalIntObjectHashMap<>(280);
    InternalIntObjectHashMap<ChannelHandlerContext> channelHandlerContextMap = new InternalIntObjectHashMap<>(524288);
    ByteBuf resByteBuf;
    //int id = 0;

    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
    private static int zero = (int) '0';
    private static int length = HTTP_HEAD.length + RN_2.length;
    private BalanceService balanceService = new BalanceServiceImpl();
    private int sendCounter = 0;
    private ChannelFuture channelFuture;

    SpscLinkedQueue<ByteBuf> writeQueue = new SpscLinkedQueue<ByteBuf>();
    static AtomicInteger num = new AtomicInteger(0);

    ByteBuf cumulation;

    private Object NULL_OBJECT = new Object();

    public void initConsumerClient(ChannelHandlerContext channelHandlerContext) {
        resByteBuf = channelHandlerContext.alloc().directBuffer(500).writeBytes(HTTP_HEAD);
        List<Endpoint> endpoints;
        try {
            endpoints = new EtcdRegistry(System.getProperty(Constants.ETCE))
                    .find(Constants.SERVER_NAME);
        } catch (Exception e) {
            log.error("get etcd fail", e);
            throw new IllegalStateException(e);
        }
        int initNodePort = balanceService.getInitNode();
        if (initNodePort == 0) {
            log.error("initNodePort = 0");
            return;
        }
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getPort() == initNodePort) {
                log.info("注册中心找到的endpoint host:{},port:{}", endpoint.getHost(), endpoint.getPort());
                Bootstrap bootstrap = new Bootstrap();
                channelFuture = bootstrap.channel(NioSocketChannel.class)
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator.DEFAULT)
//                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Constants.FIXED_RECV_BYTEBUF_ALLOCATOR))
                        .option(ChannelOption.SO_RCVBUF, Constants.RECEIVE_BUFFER_SIZE)
                        .option(ChannelOption.SO_SNDBUF, Constants.SEND_BUFFER_SIZE)
                        .handler(new ChannelInboundHandlerAdapter() {
        /*                    @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                try {
                                    while (byteBuf.readableBytes() > HTTP_HEAD.length + 8) {
                                        byteBuf.markReaderIndex();
                                        //读id
                                        int id = byteBuf.readInt();
                                        //读整个数据的长度
                                        int dataLength = byteBuf.readInt();
                                        //content-length占的位数不定，小于10占1byte（1-9），>= 则2byte
                                        int byteToSkip = dataLength < 10 ? 1 : 2;
                                        //可以读的长度应该是：固定的http头长度+\r\n长度+数据长度+content-length长度（不固定）
                                        if (byteBuf.readableBytes() < length + dataLength + byteToSkip) {
                                            byteBuf.resetReaderIndex();
                                            return;
                                        }
                                        ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                        if (client != null) {
                                            //消息的格式为： 4byte（int长度）+ 4byte（int id）+ provider agent完整拼接好的http response
                                            //由于前面读了长度和id,后面就是完整的http结果了，直接slice
                                            *//*client.writeAndFlush(byteBuf.slice(byteBuf.readerIndex(), length + dataLength + byteToSkip).retain()
                                                    , client.voidPromise());*//*

                                            //设置读写范围，这样可以避免slice
                                            byteBuf.markWriterIndex();
                                            byteBuf.writerIndex(byteBuf.readerIndex() + length + dataLength + byteToSkip);
                                            client.writeAndFlush(byteBuf.retain()
                                                    , client.voidPromise());
                                            byteBuf.resetWriterIndex();
                                        } else {
                                            log.error("client is null .id:{}", id);
                                        }
                                        byteBuf.readerIndex(byteBuf.readerIndex() + length + dataLength + byteToSkip);
                                    }
                                } finally {
                                    ReferenceCountUtil.release(msg);
                                }
                            }*/

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                try {
                                    if (msg instanceof ByteBuf) {
                                        ByteBuf data = (ByteBuf) msg;
                                        if (cumulation == null) {
                                            cumulation = data;
                                            try {
                                                callDecode(ctx, cumulation);
                                            } finally {
                                                if (cumulation != null && !cumulation.isReadable()) {
                                                    cumulation.release();
                                                    cumulation = null;
                                                }
                                            }
                                        } else {
                                            try {
                                                if (cumulation.writerIndex() > cumulation.maxCapacity() - data.readableBytes()) {
                                                    ByteBuf oldCumulation = cumulation;
                                                    cumulation = ctx.alloc().heapBuffer(oldCumulation.readableBytes() + data.readableBytes());
                                                    // 发生了数据拷贝，可以优化掉
                                                    cumulation.writeBytes(oldCumulation);
                                                    oldCumulation.release();
                                                }
                                                // 发生了数据拷贝，可以优化掉
                                                cumulation.writeBytes(data);
                                                callDecode(ctx, cumulation);
                                            } finally {
                                                if (cumulation != null) {
                                                    if (cumulation != null) {
                                                        if (!cumulation.isReadable() && cumulation.refCnt() == 1) {
                                                            cumulation.release();
                                                            cumulation = null;
                                                        } else {
                                                            if (cumulation.refCnt() == 1) {
                                                                cumulation.discardSomeReadBytes();
                                                            }
                                                        }
                                                    }
                                                }
                                                data.release();
                                            }
                                        }
                                    }
                                } catch (DecoderException e) {
                                    throw e;
                                } catch (Throwable t) {
                                    throw new DecoderException(t);
                                }
                            }

                            protected void callDecode(ChannelHandlerContext ctx, ByteBuf in) {
                                try {
                                    while (in.isReadable()) {

                                        int oldInputLength = in.readableBytes();
                                        decode(ctx, in);

                                        // See https://github.com/netty/netty/issues/1664
                                        if (ctx.isRemoved()) {
                                            break;
                                        }

                                        if (oldInputLength == in.readableBytes()) {
                                            break;
                                        } else {
                                            continue;
                                        }
                                    }
                                } catch (DecoderException e) {
                                    throw e;
                                } catch (Throwable cause) {
                                    throw new DecoderException(cause);
                                }
                            }

                            protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {

                                while (byteBuf.readableBytes() > HTTP_HEAD.length + 8) {
                                    int savedReaderIndex = byteBuf.readerIndex();
                                    //读id
                                    int id = byteBuf.readInt();
                                    //读整个数据的长度
                                    int dataLength = byteBuf.readInt();
                                    //content-length占的位数不定，小于10占1byte（1-9），>= 则2byte
                                    int byteToSkip = dataLength < 10 ? 1 : 2;
                                    //可以读的长度应该是：固定的http头长度+\r\n长度+数据长度+content-length长度（不固定）
                                    if (byteBuf.readableBytes() < length + dataLength + byteToSkip) {
                                        byteBuf.readerIndex(savedReaderIndex);
                                        return;
                                    }
                                    ChannelHandlerContext client = channelHandlerContextMap.remove(id);
                                    int len = byteBuf.readerIndex() + length + dataLength + byteToSkip;
                                    if (client != null) {
                                        //消息的格式为： 4byte（int长度）+ 4byte（int id）+ provider agent完整拼接好的http response
                                        //由于前面读了长度和id,后面就是完整的http结果了，直接slice
//                                    client.writeAndFlush(byteBuf.slice(byteBuf.readerIndex(), length + dataLength + byteToSkip).retain()
//                                            , client.voidPromise());

                                        //设置读写范围，这样可以避免slice
                                        byteBuf.markWriterIndex();
                                        byteBuf.writerIndex(len);

                                        client.writeAndFlush(byteBuf.retain(), client.voidPromise());
                                        byteBuf.resetWriterIndex();
                                    } else {
                                        log.error("client is null .id:{}", id);
                                    }
                                    byteBuf.readerIndex(len);
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                log.error("consumer hander closed");
                                try {
                                    if (cumulation != null) {
                                        callDecode(ctx, cumulation);
                                        decodeLast(ctx, cumulation);
                                    } else {
                                        decodeLast(ctx, Unpooled.EMPTY_BUFFER);
                                    }
                                } catch (DecoderException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new DecoderException(e);
                                } finally {
                                    if (cumulation != null) {
                                        cumulation.release();
                                        cumulation = null;
                                    }
                                }
                            }

                            @Override
                            public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                                ByteBuf buf = cumulation != null ? cumulation : Unpooled.EMPTY_BUFFER;
                                int readable = buf.readableBytes();
                                if (buf.isReadable()) {
                                    ByteBuf bytes = buf.readBytes(readable);
                                    buf.release();
                                    ctx.fireChannelRead(bytes);
                                }
                                cumulation = null;
                            }

                            protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                                decode(ctx, in);
                            }

                        }).group(channelHandlerContext.channel().eventLoop())
                        .connect(
                                new InetSocketAddress(endpoint.getHost(), endpoint.getPort()))
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {

                                    Channel ch = future.channel();
                                    ByteBuf buf = null;
                                    boolean flush = !writeQueue.isEmpty();
                                    while ((buf = writeQueue.poll()) != null) {
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
                log.info("创建到provider agent的连接成功,hots:{},port:{}", endpoint.getHost(), endpoint.getPort());
            }
            //log.info("channelFutureMap key有：{}", JSON.toJSONString(channelFutureMap.keySet()));
        }

    }

    public void send(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
        int id = balanceService.getId();
        //总长度在外面已经写了，直接跳到4，写id
        byteBuf.setInt(4, id);
        channelHandlerContextMap.put(id, channelHandlerContext);

        if (channelFuture != null && channelFuture.isSuccess()) {
//            log.info("num:{}", num.incrementAndGet());
            Channel channel = channelFuture.channel();
            if (++sendCounter < Constants.BATCH_SIZE) {
                channel.write(byteBuf, channel.voidPromise());
            } else {
                channel.writeAndFlush(byteBuf, channel.voidPromise());
                sendCounter = 0;
            }

            if (!channel.isWritable()) {
                log.info(" unwritable channel:" + channel + ", bytesBeforeUnwritable" + channel.bytesBeforeUnwritable());
            }

        } else {
            ReferenceCountUtil.release(byteBuf);
            ByteBuf res = channelHandlerContext.alloc().directBuffer();
            res.writeBytes(HTTP_HEAD);
            res.writeByte(zero + 1);
            res.writeBytes(RN_2);
            res.writeByte('1');
            channelHandlerContext.writeAndFlush(res, channelHandlerContext.channel().voidPromise());
        }

    }

    public void flush(ChannelHandlerContext channelHandlerContext) {
        channelHandlerContext.flush();
    }

    public ChannelFuture getChannel(int port) {
        return channelFuture;
    }
}
