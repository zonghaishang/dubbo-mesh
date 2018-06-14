package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.util.Constants;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerServer {
    private static final Logger log = LoggerFactory.getLogger(ConsumerServer.class);
    private static int consumerServerPort = Integer.valueOf(System.getProperty(Constants.SERVER_PORT, "20000"));

    public static void initConsumerAgent() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup boss = new NioEventLoopGroup(Constants.EVENT_LOOP_NUM);
        //EventLoopGroup boss0 = new NioEventLoopGroup(Constants.EVENT_LOOP_NUM);
        //((NioEventLoopGroup) boss).setIoRatio(100);
        bootstrap.group(boss)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Constants.FIXED_RECV_BYTEBUF_ALLOCATOR))
                .option(ChannelOption.SO_RCVBUF, Constants.RECEIVE_BUFFER_SIZE)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Constants.CONNECT_TIME_OUT)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(Constants.FIXED_RECV_BYTEBUF_ALLOCATOR));
                        ch.config().setConnectTimeoutMillis(Constants.CONNECT_TIME_OUT);
                        ch.config().setAllocator(PooledByteBufAllocator.DEFAULT);
                        ch.config().setReceiveBufferSize(Constants.RECEIVE_BUFFER_SIZE);
                        ch.config().setSendBufferSize(Constants.SEND_BUFFER_SIZE);
                        ch.config().setTcpNoDelay(true);
                        ch.config().setKeepAlive(true);
                        ch.pipeline().addLast(new InternalReadTimeoutHandler(2000, TimeUnit.MILLISECONDS));
                        ch.pipeline().addLast(new ConsumerHandler());
                    }
                });
        try {
            log.info("开始创建服务,consumerServerPort:{}", consumerServerPort);
            bootstrap.bind(consumerServerPort)
                    .sync().channel().closeFuture().sync();
            log.info("创建consumer服务成功");
        } catch (InterruptedException e) {
            log.error("创建consumer服务失败");
        } finally {
            boss.shutdownGracefully();
        }
    }
}
