package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.util.CodecOutputList;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author 景竹 2018/5/12
 * @author yiji@apache.org
 */
public class ProviderHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProviderHandler.class);
    private static FastThreadLocal<ProviderClient> threadLocal = new FastThreadLocal<>();
    ;

    private static final int STR_LENGTH = 173;

    private Object NULL_OBJECT = new Object();

    ByteBuf cumulation;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (threadLocal.get() == null) {
            log.info("init providerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            ProviderClient providerClient = new ProviderClient();
            providerClient.initProviderClient(ctx);
            threadLocal.set(providerClient);
        }
    }

//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg){
//        ByteBuf byteBuf = (ByteBuf)msg;
//        try {
//            while (byteBuf.isReadable()){
//                byteBuf.markReaderIndex();
//                //接收到的消息格式为： 4byte（总消息长度）+ 4byte（id）+ 具体param消息
//                int readableBytes = byteBuf.readableBytes();
//                if(readableBytes < 4){
//                    byteBuf.resetReaderIndex();
//                    return;
//                }
//                int allDataLength = byteBuf.readInt();
//                //已经读了一个int
//                if(byteBuf.readableBytes() < allDataLength){
//                    byteBuf.resetReaderIndex();
//                    return;
//                }
//                //
//                int id = byteBuf.readInt();
//                //dubboRequest encode
//
//                //已经读了一个id了，因此-4，剩余为param长度
//                int parameterLength = allDataLength - 4;
//                //parameterLength + STR_LENGTH = dubbo头的长度固定+参数长度
//                threadLocal.get().send(ctx,id,parameterLength + STR_LENGTH,byteBuf.slice(byteBuf.readerIndex(),parameterLength));
//                byteBuf.readerIndex(byteBuf.readerIndex() + parameterLength);
//            }
//        }finally {
//            if(byteBuf.refCnt() != 0){
//                ReferenceCountUtil.release(msg);
//            }
//        }
//    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            if (msg instanceof ByteBuf) {
                ByteBuf data = (ByteBuf) msg;
                if (cumulation == null) {
                    cumulation = data;
                    try {
                        callDecode(ctx, cumulation, out);
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
                            cumulation = ctx.alloc().directBuffer(oldCumulation.readableBytes() + data.readableBytes());
                            // 发生了数据拷贝，可以优化掉
                            cumulation.writeBytes(oldCumulation);
                            oldCumulation.release();
                        }
                        // 发生了数据拷贝，可以优化掉
                        cumulation.writeBytes(data);
                        callDecode(ctx, cumulation, out);
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
            } else {
                out.add(msg);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new DecoderException(t);
        } finally {
            out.recycle();
        }
    }

    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, CodecOutputList out) {
        try {
            while (in.isReadable()) {
                int outSize = out.size();

                int oldInputLength = in.readableBytes();
                decode(ctx, in, out);

                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (outSize == out.size()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Throwable cause) {
            throw new DecoderException(cause);
        }
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        int savedReaderIndex = byteBuf.readerIndex();

        //接收到的消息格式为： 4byte（总消息长度）+ 4byte（id）+ 具体param消息
        int readableBytes = byteBuf.readableBytes();
        if (readableBytes < 4) {
            byteBuf.readerIndex(savedReaderIndex);
            return;
        }
        int allDataLength = byteBuf.readInt();
        //已经读了一个int
        if (byteBuf.readableBytes() < allDataLength) {
            byteBuf.readerIndex(savedReaderIndex);
            return;
        }
        //
        int id = byteBuf.readInt();
        //dubboRequest encode

        // 标识是成功解码，send参数需要做合并
        out.add(NULL_OBJECT);
        //已经读了一个id了，因此-4，剩余为param长度
        int parameterLength = allDataLength - 4;
        //parameterLength + STR_LENGTH = dubbo头的长度固定+参数长度
        threadLocal.get().send(ctx, id, parameterLength + STR_LENGTH, byteBuf.slice(byteBuf.readerIndex(), parameterLength));
        byteBuf.readerIndex(byteBuf.readerIndex() + parameterLength);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.error("consumer hander closed");
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            if (cumulation != null) {
                callDecode(ctx, cumulation, out);
                decodeLast(ctx, cumulation, out);
            } else {
                decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
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

            out.recycle();
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

    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);
    }


}
