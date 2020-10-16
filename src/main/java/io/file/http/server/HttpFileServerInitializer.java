package io.file.http.server;

import io.file.http.handler.LastIn;
import io.file.http.handler.download.HttpFileDownloadServerHandler;
import io.file.http.handler.upload.HttpFileUploadServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author SuanCaiYv
 * @time 2020/9/13 下午10:46
 */
public class HttpFileServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;

    public HttpFileServerInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        EventLoopGroup eventExecutors = new DefaultEventLoopGroup(12);

        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(eventExecutors, new HttpFileUploadServerHandler());
        pipeline.addLast(eventExecutors, new HttpObjectAggregator(1024*1024*1024));
        pipeline.addLast(eventExecutors, new ChunkedWriteHandler());
        pipeline.addLast(eventExecutors, new HttpFileDownloadServerHandler());
        pipeline.addLast(new LastIn());
        // pipeline.addLast(eventExecutors, new TestIn());
    }

    /**
     * 此方法没有调用，仅作为笔记使用。
     *
     * 经过实测，耗时操作如果不使用额外的线程处理，会阻塞其他连接，比如A连接耗时10ms，B连接在第5毫秒连接，且耗时也是10ms；
     * 那么B完成时不是15ms而是20ms，因为必须等待A完成。
     * 所以耗时操作，要么提交到线程池，要么给Handler绑定一个EventLoopGroup。
     *
     * 第二，每个连接建立一个Channel，每个Channel绑定到一个EventLoop；
     * 每个Channel被绑定一个ChannelPipline，这个ChannelPipline又含有多个Handler，每个Handler都有一个ChannelHandlerContext与之绑定；
     * 所以ChannelPipline间接于ChannelHandlerContext绑定。通过ChannelHandlerContext可以访问到它对应的Handler的前一个或后一个Handler，这也就是fireXXX()方法的来源。
     * 最后，每一个Channel都会被分配一个全新的，new出来的Pipline，也就是说每个Channel都有自己的ChannelHandlerContext和ChannelHandler。
     *
     * 然后EventLoop轮询绑定到它身上的每一个Channel，看看是否有事件到达，如果是，就调用与之绑定的Pipline。
     * 所以不要把耗时事件直接使用ChannelHandler处理，这会阻塞轮询，导致其他Channel被阻塞。
     */
    public void note() {
        ;
    }
}
