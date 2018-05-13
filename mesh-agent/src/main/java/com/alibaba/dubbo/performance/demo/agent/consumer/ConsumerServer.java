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

/**
 * @author 景竹 2018/5/12
 */
public class ConsumerServer {
    private static final Logger log = LoggerFactory.getLogger(ConsumerServer.class);
    private static int consumerServerPort = Integer.valueOf(System.getProperty(Constants.SERVER_PORT, "20000"));

    public static void initConsumerAgent(){
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup worker = new NioEventLoopGroup(8);
        bootstrap.group(worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(10 * 1024))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch){
                        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(8 * 1024));
                        ch.config().setConnectTimeoutMillis(300);
                        ch.config().setAllocator(PooledByteBufAllocator.DEFAULT);
                        ch.pipeline().addLast(new ConsumerHandler());
                    }
                });
        try {
            bootstrap.bind(consumerServerPort)
                    .sync().channel().closeFuture().sync();
            log.info("创建consumer服务成功");
        } catch (InterruptedException e) {
            log.error("创建consumer服务失败");
        }finally {
            worker.shutdownGracefully();
        }
    }
}
