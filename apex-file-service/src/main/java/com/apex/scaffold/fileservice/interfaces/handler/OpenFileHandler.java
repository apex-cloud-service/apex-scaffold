package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.application.service.FileService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import java.io.IOException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 开放文件接口：只暴露上传与按对象键读取，主体账号由认证过滤器注入的
 * {@code FileAccessContext} 决定，属主校验继续复用 {@link FileService} 内部不变量。
 *
 * <p>管理端的删除、孤儿清理、分享等操作不在此处开放，避免第三方应用获得超出 scope
 * 语义的管理能力。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class OpenFileHandler {

    private final FileService files;

    private final RequestHandlerUtil responses;

    private final String defaultBucket;

    public OpenFileHandler(
            FileService files,
            RequestHandlerUtil responses,
            @org.springframework.beans.factory.annotation.Value("${storage.bucket-name:apex}") String defaultBucket) {
        this.files = files;
        this.responses = responses;
        this.defaultBucket = defaultBucket;
    }

    /**
     * 开放上传：普通文件（非图片）落默认桶，owner 取可信主体。
     *
     * @param request multipart 请求
     * @return 上传结果
     */
    public Mono<ServerResponse> upload(ServerRequest request) {
        return request.multipartData().flatMap(parts -> {
            if (!(parts.getFirst("file") instanceof FilePart file)) {
                return responses.buildErrorResponse(ResultCode.PARAM_ERROR, "上传文件不能为空");
            }
            return DataBufferUtils.join(file.content())
                    .flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        try {
                            buffer.read(bytes);
                            return files.upload(
                                    file.filename(), bytes, MediaType.APPLICATION_OCTET_STREAM_VALUE, null, null);
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .flatMap(result -> responses.buildSuccessResponse(result));
        });
    }

    /**
     * 开放读取：按 {@code bucketName}/{@code fileName} 读取，owner 校验在服务层完成。
     *
     * @param request 查询参数 bucketName、fileName
     * @return 文件流
     */
    public Mono<ServerResponse> content(ServerRequest request) {
        String fileName = request.queryParam("fileName").orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return responses.buildErrorResponse(ResultCode.PARAM_ERROR, "文件名称不能为空");
        }
        String bucket = request.queryParam("bucketName").orElse(defaultBucket);
        return files.download(bucket, fileName).flatMap(stream -> {
            Flux<DataBuffer> body = DataBufferUtils.readInputStream(
                            () -> stream, DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(BlockingIoSchedulers.storage())
                    .doFinally(signal -> closeQuietly(stream));
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body, DataBuffer.class);
        });
    }

    private void closeQuietly(java.io.InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 关闭失败不影响已发出的响应；连接资源由存储适配器负责最终释放。
        }
    }
}
