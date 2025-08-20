package org.offline_file;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.xml.bind.DatatypeConverter;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Offline File Service test...");

        try {
            OfflineFileService service = new OfflineFileService();

            // 1. test data
            String sender = "user1";
            String recipient = "user2";
            String fileName = "merhaba.txt";
            String fileContent = "Merhaba offline dosya";
            byte[] content = fileContent.getBytes(StandardCharsets.UTF_8);

            // 2. SHA-256 hesaplama
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            // binary hash -> lowercase hex string
            String hashHex = DatatypeConverter.printHexBinary(hashBytes).toLowerCase();

            System.out.println("Content: '" + fileContent + "'");
            System.out.println("Calculated SHA-256 Hash: " + hashHex);

            // 3. storeOfflineFile servisi
            InputStream fileStream = new ByteArrayInputStream(content);
            Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS);

            System.out.println("Attempting to store file...");
            long queueId = service.storeOfflineFile(
                    sender,
                    recipient,
                    fileName,
                    fileStream,
                    content.length,
                    hashHex, 
                    "text/plain",
                    null,
                    null,
                    null,
                    expiration
            );

            System.out.println("Successfully stored file! Queue ID: " + queueId);

        } catch (Exception e) {
            System.err.println("An error occurred during the test run:");
            e.printStackTrace();
        }
    }
}