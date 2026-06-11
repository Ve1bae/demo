package com.example.demo.service;

import com.example.demo.dto.ChunkInitResult;
import com.example.demo.dto.UploadProgress;

import java.io.InputStream;
import java.util.List;

public interface MinioStorageService {

    /** 初始化一个分片上传，返回 uploadId */
    ChunkInitResult initChunkUpload(String objectName, String contentType);

    /** 上传一个分片（partNumber 从 1 开始） */
    void uploadChunk(String objectName, String uploadId, int partNumber, InputStream data, long size, String contentType);

    /** 合并全部分片，返回可访问的 URL */
    String completeChunkUpload(String objectName, String uploadId, List<Integer> sortedPartNumbers);

    /** 查询已有进度（返回已上传的分片号列表），用于断点续传 */
    UploadProgress getUploadProgress(String objectName);

    /** 直接小文件上传（<5MB），返回 URL */
    String uploadSmallFile(String objectName, InputStream data, long size, String contentType);

    /** 获取文件访问 URL */
    String getPresignedUrl(String objectName);

    /** 删除对象 */
    void delete(String objectName);
}
