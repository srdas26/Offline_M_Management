package org.offline_file;

import java.io.ByteArrayInputStream;
import java.time.Instant;

public class Main {
    public static void main(String[] args) throws Exception {
        OfflineFileService service = new OfflineFileService();

        // Örnek kullanım
        String sender = "user1";
        String recipient = "user2";
        String fileName = "example.txt";
        byte[] content = "Merhaba offline dosya".getBytes();
        String hashHex = "b1946ac92492d2347c6235b4d2611184"; // örnek, gerçek SHA-256 olmalı

        long queueId = service.storeOfflineFile(
                sender,
                recipient,
                fileName,
                new ByteArrayInputStream(content),
                content.length,
                hashHex,
                "text/plain",
                null,
                null,
                null,
                Instant.now().plusSeconds(3600)
        );

        System.out.println("Queued offline file id: " + queueId);
    }
}
