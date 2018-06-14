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
import java.util.List;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerClient {
    private static final Logger log = LoggerFactory.getLogger(ConsumerClient.class);
    //InternalIntObjectHashMap<ChannelFuture> channelFutureMap = new InternalIntObjectHashMap<>(280);
    InternalIntObjectHashMap<ChannelHandlerContext> channelHandlerContextMap = new InternalIntObjectHashMap<>((Constants.MASK + 1) * 2);
    ByteBuf resByteBuf;
    //int id = 0;

    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte[] RN_2 = "\r\n\n".getBytes();
    private static int HeaderLength = 90;
    private static int zero = (int) '0';
    private BalanceService balanceService = new BalanceServiceImpl() {
    };
    private int sendCounter = 0;
    private ChannelFuture channelFuture;

    SpscLinkedQueue<ByteBuf> writeQueue = new SpscLinkedQueue<ByteBuf>();


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
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Constants.FIXED_RECV_BYTEBUF_ALLOCATOR))
                        .option(ChannelOption.SO_RCVBUF, Constants.RECEIVE_BUFFER_SIZE)
                        .option(ChannelOption.SO_SNDBUF, Constants.SEND_BUFFER_SIZE)
                        .handler(new ChannelInboundHandlerAdapter() {

                            @Override
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
                                        if (byteBuf.readableBytes() < HTTP_HEAD.length + dataLength + RN_2.length + byteToSkip) {
                                            byteBuf.resetReaderIndex();
                                            return;
                                        }
                                        //不remove，只get，map的槽位循环利用
                                        int index = id & Constants.MASK;
                                        ChannelHandlerContext client = channelHandlerContextMap.get(index);
                                        if (client != null) {
                                            //消息的格式为： 4byte（int长度）+ 4byte（int id）+ provider agent完整拼接好的http response
                                            //由于前面读了长度和id,后面就是完整的http了，直接slice返回即可
                                            client.writeAndFlush(byteBuf.slice(byteBuf.readerIndex(), HTTP_HEAD.length + dataLength + RN_2.length + byteToSkip).retain()
                                                    , client.voidPromise());
                                        } else {
                                            log.error("client is null .id:{}", id);
                                        }
                                        byteBuf.readerIndex(byteBuf.readerIndex() + HTTP_HEAD.length + dataLength + RN_2.length + byteToSkip);
                                        //balanceService.releaseCount(ctx.channel().remoteAddress());
                                    }
                                } finally {
                                    ReferenceCountUtil.release(msg);
                                }
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
        //byteBuf.markWriterIndex();
        //总长度在外面已经写了，直接跳到4，写id
        byteBuf.setInt(4, id);
        //byteBuf.writeInt(id);
        //byteBuf.resetWriterIndex();

        //id & Constants.MASK 等于id取模，让map的槽位复用，都是put，不remove了
        channelHandlerContextMap.put(id & Constants.MASK, channelHandlerContext);

        if (channelFuture != null && channelFuture.isSuccess()) {
            Channel channel = channelFuture.channel();
            if (++sendCounter < Constants.BATCH_SIZE) {
                channel.write(byteBuf, channel.voidPromise());
            } else {
                channel.writeAndFlush(byteBuf, channel.voidPromise());
                //balanceService.addCount(currentPort,Constants.BATCH_SIZE);
                sendCounter = 0;
            }
        } else {
            //System.out.println("back directly");
            //balanceService.releaseCount(currentPort);
            ReferenceCountUtil.release(byteBuf);
            ByteBuf res = channelHandlerContext.alloc().directBuffer();
            res.writeBytes(HTTP_HEAD);
            res.writeByte(zero + 0);
            res.writeBytes(RN_2);
            channelHandlerContext.writeAndFlush(res, channelHandlerContext.channel().voidPromise());
        }

    }

    public ChannelFuture getChannel(int port) {
        return channelFuture;
    }
}
