package com.alibaba.dubbo.performance.demo.agent.consumer;

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
public class ConsumerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ConsumerHandler.class);

    private static FastThreadLocal<ConsumerClient> threadLocal = new FastThreadLocal<>();
    private static byte[] HTTP_HEAD = ("HTTP/1.1 200 OK\r\n" +
            "content-type: text/json\r\n" +
            "connection: keep-alive\r\n" +
            "content-length: ").getBytes();
    private static byte r = '\r';
    private static int zero = (int) '0';
    byte[] lengthBytes = new byte[5];

    ByteBuf cumulation;
    private boolean singleDecode;
    private boolean decodeWasNull;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (threadLocal.get() == null) {
            log.info("init consumerClient,ctx:{},thread id:{}", ctx.channel().id(), Thread.currentThread().getId());
            ConsumerClient consumerClient = new ConsumerClient();
            consumerClient.initConsumerClient(ctx);
            threadLocal.set(consumerClient);
        }
    }

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
            // 触发逻辑
            if (!out.isEmpty()) {
                ConsumerClient client = threadLocal.get();
                for (int i = 0; i < out.size(); i++) {
                    ByteBuf buf = (ByteBuf) out.getUnsafe(i);
                    // 这里直接解耦write和flush
                    client.send(ctx, buf);
                }
            }

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

        int totalLength = byteBuf.readableBytes();
        if (totalLength < HTTP_HEAD.length) {
            byteBuf.readerIndex(savedReaderIndex);
            return;
        }
        int contentLength = 0;
        //直接跳到content-length开始读
        byteBuf.readerIndex(33);

        //由于content-length后面数字长度不定，因此在header_length的基础上增加
        int header_length = 143;

        //把整个content-length后面5个byte全读出来，最大长度肯定不超过5
        byteBuf.readBytes(lengthBytes);
        for (int i = 0; i < lengthBytes.length; i++) {
            if (lengthBytes[i] != r) {
                //不等于\r则说明还是数字部分，结束会以\r结尾
                contentLength = contentLength * 10 + lengthBytes[i] - zero;
                header_length++;
            } else {
                break;
            }
        }
        //判断剩余字节够不够读
        if (totalLength - header_length < contentLength) {
            byteBuf.readerIndex(savedReaderIndex);
            return;
        }

        //前面的消息体中，前面接口、类型等都是固定的，长度136，直接跳到param开始读
        int paramStart = header_length + 136;
        //整个消息的长度减去前面接口、类型等固定长度，得到param的长度
        int paramLength = contentLength - 136;

        byteBuf.markWriterIndex();
        //直接复用传过来的byteBuf，param往前移动8byte，一个用于记录总长度，一个用于保存ID
        byteBuf.writerIndex(paramStart - 8);
        //数据总长度
        byteBuf.writeInt(paramLength + 4);
        //byteBuf.writeInt(0);
        byteBuf.resetWriterIndex();

        // threadLocal.get().send(ctx, byteBuf.slice(paramStart - 8, paramLength + 8).retain());

        out.add(byteBuf.slice(paramStart - 8, paramLength + 8).retain());

        byteBuf.readerIndex(paramStart + paramLength);
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

            if (!out.isEmpty()) {
                if (!out.isEmpty()) {
                    ConsumerClient client = threadLocal.get();
                    for (int i = 0; i < out.size(); i++) {
                        ByteBuf buf = (ByteBuf) out.getUnsafe(i);
                        // 这里直接解耦write和flush
                        client.send(ctx, buf);
                    }
                }
                out.clear();
            }

            ctx.fireChannelInactive();
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


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("consumer server error: ", cause);
    }
}
