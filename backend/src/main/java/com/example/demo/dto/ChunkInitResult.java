package com.example.demo.dto;

import java.util.List;

public class ChunkInitResult {
    private String uploadId;
    private List<Integer> uploadedParts;
    private String objectName;
    private boolean created;

    public ChunkInitResult() {}

    public ChunkInitResult(String uploadId, List<Integer> uploadedParts, String objectName, boolean created) {
        this.uploadId = uploadId;
        this.uploadedParts = uploadedParts;
        this.objectName = objectName;
        this.created = created;
    }

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public List<Integer> getUploadedParts() { return uploadedParts; }
    public void setUploadedParts(List<Integer> uploadedParts) { this.uploadedParts = uploadedParts; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public boolean isCreated() { return created; }
    public void setCreated(boolean created) { this.created = created; }
}
