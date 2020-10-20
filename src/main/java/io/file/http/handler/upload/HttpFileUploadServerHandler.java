package io.file.http.handler.upload;

import io.file.http.system.SystemConstant;
import io.file.http.util.HttpResponseUtil;
import io.file.http.util.RedisUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.logging.Logger;

public class HttpFileUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private HttpRequest request;

    private HttpData partialContent;

    private String dire;

    private String newFileName;

    // 这儿他妈的有bug。本来设置一个size指出如果文件大小过大，就用硬盘，否则内存，结果只能是硬盘？
    // Netty说13年就发现了，修复了，结果还是存在，遂我在这里也设置只用硬盘，这样做的结果就是会卡，待我后面自己修复
    // 具体链接：https://github.com/netty/netty/issues/1727
    // 像他妈做梦一样。
    private static final HttpDataFactory factory =
            // Disk if size exceed
            new DefaultHttpDataFactory(SystemConstant.FILE_USED_MEMORY_SIZE);

    private HttpPostRequestDecoder decoder;

    static {
        // 如果存在就删除
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        // 系统默认临时文件路径
        DiskFileUpload.baseDirectory = null;
        // 存在就删除
        DiskAttribute.deleteOnExitTemporaryFile = true;
        // 系统默认的临时路径
        DiskAttribute.baseDirectory = null;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        // 如果是请求头
        if (msg instanceof HttpRequest) {
            decoder = null;
            HttpRequest request = this.request = (HttpRequest) msg;
            String uri = request.uri();
            // 如果是上传请求且请求方式满足
            if (uri.contains(SystemConstant.FILE_UPLOAD_PATH) && HttpMethod.POST.equals(request.method())) {
                // 路径里面的键值对
                QueryStringDecoder decoderQuery = new QueryStringDecoder(request.uri());
                decoder = new HttpPostRequestDecoder(factory, request);
                String uuid = uri.substring(uri.lastIndexOf("/")+1);
                String str = RedisUtil.get(uuid);
                if (str == null || "null".equals(str)) {
                    HttpResponseUtil.writeError(ctx.channel(), "请求非法 请携带合法的路径参数", true);
                }
                String[] strings = RedisUtil.get(uuid).trim().split(";");
                dire = strings[0];
                if (strings.length == 2) {
                    newFileName = strings[1];
                } else {
                    newFileName = null;
                }
            }
            // 否则转发到后面去
            else {
                ctx.fireChannelRead(msg);
            }
        }
        // 如果转发
        else if (decoder == null) {
            ctx.fireChannelRead(msg);
        }
        // 否则处理
        else {
            if (msg instanceof HttpContent) {
                // 接收到了新的块
                HttpContent chunk = (HttpContent) msg;
                decoder.offer(chunk);
                // 这个方法调用的位置也可以是读到末尾的时候
                // 区别在于，在尾部调用，必须等此次请求发送完毕才可以把文件存在硬盘；相当于一次性把所有文件都接收再存储(可以上传多个文件)
                // 不在尾部调用，则接收到一个文件就存一个，代价是导致请求比较慢，但是好处是服务器不需要一次性处理所有文件。
                try {
                    readHttpDataChunkByChunk();
                } catch (IOException e) {
                    HttpResponseUtil.writeError(ctx.channel(), "服务器异常", true);
                }
                if (chunk instanceof LastHttpContent) {
                    HttpResponseUtil.writeOK(ctx.channel(), false);
                    reset();
                }
            }
        }
    }

    private void reset() {
        request = null;
        // 调用destroy()方法来释放所有资源
        decoder.destroy();
        decoder = null;
    }

    private void readHttpDataChunkByChunk() throws IOException {
        // hasNext()满足的条件是：此时已经有了一个完整的文件，或一个form表单键值对。
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                writeHttpData(data);
            }
        }
    }

    // 真正写入数据的
    private void writeHttpData(InterfaceHttpData data) throws IOException {
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            // 处理上传数据为表单时，一次接收一个键值对
            // DiskAttribute可以获得表单数据，getName()是key，getValue()是value
            Attribute attribute = (Attribute) data;
            String name;
            String value;
            name = attribute.getName();
            value = attribute.getValue();
        } else {
            if (data.getHttpDataType() == HttpDataType.FileUpload) {
                // 处理上传数据为文件时
                FileUpload fileUpload = (FileUpload) data;
                if (fileUpload.isCompleted()) {
                    if (newFileName == null) {
                        newFileName = fileUpload.getFilename();
                    } else {
                        String originalName = fileUpload.getFilename();
                        newFileName = newFileName + originalName.substring(originalName.lastIndexOf("."));
                    }
                    final File file = new File(dire + "/" + newFileName);
                    System.out.println(file.getAbsolutePath());
                    FileChannel inputChannel = new FileInputStream(fileUpload.getFile()).getChannel();
                    FileChannel outputChannel = new FileOutputStream(file).getChannel();
                    outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
                    inputChannel.close();
                    outputChannel.close();
                    // 其他方法
                    // fileUpload.isInMemory(); tells if the file is in Memory or on File
                    // fileUpload.renameTo(dest); // enable to move into another File dest
                    // decoder.removeFileUploadFromClean(fileUpload); remove the File of file list to delete file
                }
            }
        }
    }
}