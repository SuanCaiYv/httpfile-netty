package io.file.http.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import static io.netty.buffer.Unpooled.copiedBuffer;

/**
 * @author SuanCaiYv
 * @time 2020/9/14 下午2:05
 */
public class HttpResponseUtil {

    public static void writeOK(Channel channel, boolean forceClose) {
        ByteBuf buf = copiedBuffer("OK", CharsetUtil.UTF_8);
        f(channel, forceClose, buf);
    }

    public static void writeError(Channel channel, String msg, boolean forceClose) {
        ByteBuf buf = copiedBuffer(msg, CharsetUtil.UTF_8);
        f(channel, forceClose, buf);
    }

    private static void f(Channel channel, boolean forceClose, ByteBuf buf) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());

        if (forceClose) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ChannelFuture future = channel.writeAndFlush(response);
        if (forceClose) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
