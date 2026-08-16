package com.zhikao.server.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘存储实现（开发环境，任务书 10.6：uploads/{knowledge, idiom, question}/）。
 */
@Slf4j
@Component
public class LocalDiskStorage implements FileStorage {

    @Value("${zhikao.storage.local-root:uploads}")
    private String localRoot;

    private Path resolve(String key) {
        // 防路径穿越：仅允许合法相对 key
        Path base = Paths.get(localRoot).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法存储路径: " + key);
        }
        return target;
    }

    @Override
    public void save(String key, InputStream inputStream, long size) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败: " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        Path target = resolve(key);
        try {
            return new FileInputStream(target.toFile());
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("文件删除失败: {}", key, e);
        }
    }
}
