package org.offline_file;

import org.offline_file.FileUtils;

import java.io.*;
        import java.nio.file.Path;
import java.sql.*;
        import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OfflineFileService {

    public static final int ST_PENDING = 0;
    public static final int ST_IN_PROGRESS = 1;

    // Buraya kendi Connection yönetimini koy
    private Connection getConnection() throws SQLException {
        String dbUrl = "jdbc:mysql://localhost:3306/chat_files";
        String user = "root"; // <-- Change this
        String password = "root"; // <-- Change this

        return DriverManager.getConnection(dbUrl, user, password);
    }

    public long storeOfflineFile(
            String senderId,
            String recipientId,
            String fileName,
            InputStream fileStream,
            long declaredFileSize,
            String fileHashHex,
            String mimeType,
            String encAlg,
            byte[] encIv,
            byte[] cipherTag,
            Instant expiresAtUtc
    ) throws Exception {
        Objects.requireNonNull(senderId);
        Objects.requireNonNull(recipientId);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(fileHashHex);

        byte[] hashRaw = FileUtils.hexToBytes(fileHashHex);
        String finalPath = FileUtils.buildPathForHash(fileHashHex);

        Path path = Path.of(finalPath);
        if (!java.nio.file.Files.exists(path)) {
            if (fileStream == null) {
                throw new IllegalArgumentException("fileStream required to create new content");
            }

            java.nio.file.Files.createDirectories(path.getParent());

            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream in = new java.security.DigestInputStream(fileStream, md)) {
                Path tmp = java.nio.file.Files.createTempFile(path.getParent(), "up-", ".part");
                long written;
                try (OutputStream out = java.nio.file.Files.newOutputStream(tmp,
                        java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    written = in.transferTo(out);
                }

                byte[] digest = md.digest();
                String calcHex = javax.xml.bind.DatatypeConverter.printHexBinary(digest).toLowerCase();

                if (declaredFileSize > 0 && declaredFileSize != written) {
                    java.nio.file.Files.deleteIfExists(tmp);
                    throw new IllegalStateException("size mismatch");
                }
                if (!calcHex.equals(fileHashHex.toLowerCase())) {
                    java.nio.file.Files.deleteIfExists(tmp);
                    throw new IllegalStateException("sha256 mismatch");
                }

                java.nio.file.Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
        } else {
            long existingSize = java.nio.file.Files.size(path);
            if (declaredFileSize > 0 && declaredFileSize != existingSize) {
                throw new IllegalStateException("existing file size differs for same hash");
            }
        }


        //file_name eklendi
        String insertFile = """
                INSERT INTO files (file_hash, file_size, mime_type, enc_alg, enc_iv, cipher_tag, file_name, file_path, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                file_size = VALUES(file_size),
                mime_type = VALUES(mime_type),
                enc_alg   = VALUES(enc_alg),
                enc_iv    = VALUES(enc_iv),
                cipher_tag= VALUES(cipher_tag),
                file_name = VALUES(file_name),
                file_path = VALUES(file_path)
                """;

        String insertRecipient = """
                INSERT INTO offline_recipients
                  (file_hash, recipient_id, sender_id, file_name, expires_at, status, created_at, updated_at)
                VALUES
                  (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  id = LAST_INSERT_ID(id),
                  expires_at = VALUES(expires_at),
                  updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement(insertFile);
                 PreparedStatement ps2 = c.prepareStatement(insertRecipient, Statement.RETURN_GENERATED_KEYS)) {

                ps1.setBytes(1, hashRaw);
                long actualSize = (declaredFileSize > 0) ? declaredFileSize : java.nio.file.Files.size(path);
                ps1.setLong(2, actualSize);
                if (mimeType != null) ps1.setString(3, mimeType); else ps1.setNull(3, Types.VARCHAR);
                if (encAlg != null) ps1.setString(4, encAlg); else ps1.setNull(4, Types.VARCHAR);
                if (encIv != null) ps1.setBytes(5, encIv); else ps1.setNull(5, Types.VARBINARY);
                if (cipherTag != null) ps1.setBytes(6, cipherTag); else ps1.setNull(6, Types.VARBINARY);
                ps1.setString(7, fileName); // file_name kısmı icin eklendi

                //7'den 8'e degistirildi
                ps1.setString(8, finalPath);
                ps1.executeUpdate();

                ps2.setBytes(1, hashRaw);
                ps2.setString(2, recipientId);
                ps2.setString(3, senderId);
                ps2.setString(4, fileName);
                if (expiresAtUtc != null) {
                    ps2.setTimestamp(5, Timestamp.from(expiresAtUtc));
                } else {
                    ps2.setNull(5, Types.TIMESTAMP);
                }
                ps2.executeUpdate();

                long queueId;
                try (ResultSet keys = ps2.getGeneratedKeys()) {
                    if (keys.next()) {
                        queueId = keys.getLong(1);
                    } else {
                        throw new IllegalStateException("no generated key");
                    }
                }

                c.commit();
                return queueId;

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<OfflineFile> claimPending(String recipientId, int limit) throws SQLException {
        String selectForUpdate = """
                SELECT id, sender_id, recipient_id, file_name,
                       HEX(file_hash) AS file_hash_hex,
                       created_at, expires_at, status
                FROM offline_recipients
                WHERE recipient_id = ? AND status = 0
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;

        String markInProgress = """
                UPDATE offline_recipients
                SET status = 1, delivery_attempts = delivery_attempts + 1, updated_at = NOW()
                WHERE id = ?
                """;

        List<OfflineFile> out = new ArrayList<>();

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement sel = c.prepareStatement(selectForUpdate);
                 PreparedStatement upd = c.prepareStatement(markInProgress)) {

                sel.setString(1, recipientId);
                sel.setInt(2, limit);

                List<Long> ids = new ArrayList<>();
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        ids.add(id);

                        OfflineFile of = new OfflineFile(
                                id,
                                rs.getString("sender_id"),
                                rs.getString("recipient_id"),
                                rs.getString("file_name"),
                                null,
                                rs.getString("file_hash_hex"),
                                rs.getTimestamp("created_at"),
                                rs.getTimestamp("expires_at"),
                                "in_progress"
                        );
                        out.add(of);
                    }
                }

                for (Long id : ids) {
                    upd.setLong(1, id);
                    upd.addBatch();
                }
                if (!ids.isEmpty()) upd.executeBatch();

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return out;
    }

    public void downloadFileData(long offlineId, OutputStream out) throws SQLException, IOException {
        String sql = """
                SELECT f.file_path
                FROM files f
                JOIN offline_recipients o ON o.file_hash = f.file_hash
                WHERE o.id = ?
                LIMIT 1
                """;

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, offlineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Path path = Path.of(rs.getString("file_path"));
                    try (InputStream in = java.nio.file.Files.newInputStream(path)) {
                        in.transferTo(out);
                    }
                } else {
                    throw new IllegalStateException("Offline file not found: id=" + offlineId);
                }
            }
        }
    }
}
