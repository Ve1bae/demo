package com.example.demo.dto;

import java.util.List;

public class UploadProgress {
    private String uploadId;
    private List<Integer> uploadedParts;
    private int totalParts;

    public UploadProgress() {}

    public UploadProgress(String uploadId, List<Integer> uploadedParts, int totalParts) {
        this.uploadId = uploadId;
        this.uploadedParts = uploadedParts;
        this.totalParts = totalParts;
    }

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public List<Integer> getUploadedParts() { return uploadedParts; }
    public void setUploadedParts(List<Integer> uploadedParts) { this.uploadedParts = uploadedParts; }
    public int getTotalParts() { return totalParts; }
    public void setTotalParts(int totalParts) { this.totalParts = totalParts; }
}
