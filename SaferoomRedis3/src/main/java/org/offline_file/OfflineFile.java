package org.offline_file;

import java.sql.Timestamp;


public class OfflineFile {

    private long id;
    private String senderId;
    private String recipientId;
    private String fileName;
    private byte[] fileContent; 
    private String fileHashHex;
    private Timestamp createdAt;
    private Timestamp expiresAt;
    private String status;

    public OfflineFile(long id, String senderId, String recipientId, String fileName,
                       byte[] fileContent, String fileHashHex, Timestamp createdAt,
                       Timestamp expiresAt, String status) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.fileName = fileName;
        this.fileContent = fileContent;
        this.fileHashHex = fileHashHex;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public long getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getRecipientId() { return recipientId; }
    public String getFileName() { return fileName; }
    public String getFileHashHex() { return fileHashHex; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getExpiresAt() { return expiresAt; }
    public String getStatus() { return status; }
}