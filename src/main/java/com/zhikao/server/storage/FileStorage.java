package com.zhikao.server.storage;

import java.io.InputStream;

/**
 * 文件存储抽象（任务书 2.3/10.6）。
 * 开发环境：本地磁盘 uploads/；生产环境：MinIO / OSS / OBS。
 * 业务代码只依赖本接口，不感知存储后端切换。
 */
public interface FileStorage {

    /**
     * 保存文件。
     *
     * @param key       存储相对 key（如 "knowledge/xxx.pdf"）
     * @param inputStream 文件流
     * @param size      文件大小（字节）
     */
    void save(String key, InputStream inputStream, long size);

    /** 读取文件流（不存在返回 null） */
    InputStream load(String key);

    /** 判断文件是否存在 */
    boolean exists(String key);

    /** 删除文件 */
    void delete(String key);
}
