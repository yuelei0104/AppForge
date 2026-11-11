package com.yuelei.appforge.cover;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.yuelei.appforge.config.CosClientConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class CosCoverObjectStore implements CoverObjectStore {
    private final COSClient client;
    private final CosClientConfig config;

    @Override
    public void upload(String key, Path file) {
        var metadata = new ObjectMetadata();
        metadata.setContentType("image/jpeg");
        metadata.setCacheControl("no-store");
        var request = new PutObjectRequest(config.getBucket(), key, file.toFile());
        request.setMetadata(metadata);
        request.setCannedAcl(CannedAccessControlList.Private);
        client.putObject(request);
    }

    @Override
    public byte[] read(String key) {
        try (var object = client.getObject(config.getBucket(), key);
                var input = object.getObjectContent()) {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) throw new IllegalStateException("封面文件过大");
            return bytes;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("封面读取失败");
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(config.getBucket(), key);
    }
}
