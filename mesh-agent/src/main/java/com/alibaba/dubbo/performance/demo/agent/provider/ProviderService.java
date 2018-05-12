package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.consumer.ConsumerHandler;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderService {
    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    private static int providerServerPort = Integer.valueOf(System.getProperty(Constants.NETTY_PORT));

    public static void initProviderAgent() throws Exception {
        new EtcdRegistry(System.getProperty(Constants.ETCE)).register(Constants.SERVER_NAME,providerServerPort);
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup worker = new NioEventLoopGroup();
        bootstrap.group(worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch){
                        //ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(1024));
                        ch.pipeline().addLast(new ProviderHandler());
                    }
                });
        try {
            bootstrap.bind(providerServerPort).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("创建provider服务失败");
        }finally {
            worker.shutdownGracefully();
        }
    }

}
