package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Bytes;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 景竹 2018/5/12
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProviderHandler.class);
    private static FastThreadLocal<ProviderClient> threadLocal = new FastThreadLocal<>();;

    private static final int STR_LENGTH = 173;

    @Override
    public void channelActive(ChannelHandlerContext ctx){
        if (threadLocal.get() == null) {
            log.info("init providerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            ProviderClient providerClient = new ProviderClient();
            providerClient.initProviderClient(ctx);
            threadLocal.set(providerClient);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        ByteBuf byteBuf = (ByteBuf)msg;
        try {
            while (byteBuf.isReadable()){
                byteBuf.markReaderIndex();
                //接收到的消息格式为： 4byte（总消息长度）+ 4byte（id）+ 具体param消息
                int readableBytes = byteBuf.readableBytes();
                if(readableBytes < 4){
                    byteBuf.resetReaderIndex();
                    return;
                }
                int allDataLength = byteBuf.readInt();
                //已经读了一个int
                if(byteBuf.readableBytes() < allDataLength){
                    byteBuf.resetReaderIndex();
                    return;
                }
                //
                int id = byteBuf.readInt();
                //dubboRequest encode

                //已经读了一个id了，因此-4，剩余为param长度
                int parameterLength = allDataLength - 4;
                //parameterLength + STR_LENGTH = dubbo头的长度固定+参数长度
                threadLocal.get().send(ctx,id,parameterLength + STR_LENGTH,byteBuf.slice(byteBuf.readerIndex(),parameterLength));
                byteBuf.readerIndex(byteBuf.readerIndex() + parameterLength);
            }
        }finally {
            if(byteBuf.refCnt() != 0){
                ReferenceCountUtil.release(msg);
            }
        }
    }



}
