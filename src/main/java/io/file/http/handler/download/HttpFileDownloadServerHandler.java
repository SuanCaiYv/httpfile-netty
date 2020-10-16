package io.file.http.handler.download;

import io.file.http.system.SystemConstant;
import io.file.http.util.HttpResponseUtil;
import io.file.http.util.RedisUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Netty实现文件下载
 */
public class HttpFileDownloadServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            HttpResponseUtil.writeError(ctx.channel(), "请求出错，请重试", true);
            return ;
        }
        String uri = request.uri();
        if (uri.contains(SystemConstant.FILE_DOWNLOAD_PATH)) {
            HttpResponseUtil.writeError(ctx.channel(), "请求出错，请重试", true);
            return ;
        }
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        // 获取UUID
        String uuid = uri.substring(uri.lastIndexOf(File.pathSeparator)+1);
        String path = RedisUtil.get(uuid);
        if (path == null || "null".equals(path)) {
            HttpResponseUtil.writeError(ctx.channel(), "请求出错，请重试", true);
            return ;
        }
        String filename = path.substring(path.lastIndexOf(File.pathSeparator)+1);
        RandomAccessFile randomAccessFile;
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        try {
            randomAccessFile = new RandomAccessFile(path, "r");
            long fileLength = randomAccessFile.length();
            HttpUtil.setContentLength(response, fileLength);
            // 写入初始行和头部
            ctx.write(response);
            response.headers().set("Content-Disposition", "attachment;fileName=" + filename);
            if (!keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else if (request.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            // 写入内容
            if (ctx.pipeline().get(SslHandler.class) == null) {
                ctx.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength), ctx.newProgressivePromise());
                // 写入尾流
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(randomAccessFile, 0, fileLength, SystemConstant.CHUNK_SIZE)),
                        ctx.newProgressivePromise());
                // HttpChunkedInput会自动写入尾流。
            }
        } catch (IOException e) {
            HttpResponseUtil.writeError(ctx.channel(), "文件请求出错", true);
        }
    }
}