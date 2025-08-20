CREATE TABLE IF NOT EXISTS files (
  file_hash  BINARY(32) PRIMARY KEY,
  file_size  BIGINT UNSIGNED NOT NULL,
  mime_type  VARCHAR(120),
  enc_alg    VARCHAR(32),
  enc_iv     VARBINARY(16),
  cipher_tag VARBINARY(16),
  key_id     VARCHAR(64),
  file_name  VARCHAR(255),
  file_path  VARCHAR(512) NOT NULL,
  storage_tier ENUM('local','s3','gcs') DEFAULT 'local',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_file_path (file_path)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS offline_recipients (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  file_hash    BINARY(32) NOT NULL,
  recipient_id VARCHAR(255) NOT NULL,
  sender_id    VARCHAR(255) NOT NULL,
  status       TINYINT UNSIGNED NOT NULL DEFAULT 0,  -- 0=pending,1=in_progress,2=delivered,3=seen,4=deleted
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  expires_at   DATETIME(3) NULL,
  delivery_attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  last_error   VARCHAR(255),
  file_name    VARCHAR(255),
  CONSTRAINT fk_rec_file FOREIGN KEY (file_hash) REFERENCES files(file_hash) ON DELETE CASCADE,
  UNIQUE KEY uniq_recipient_per_file (file_hash, recipient_id),
  KEY idx_recipient_status_msg (recipient_id, status, file_hash),
  KEY idx_status_expires (status, expires_at),
  KEY idx_status_updated (status, updated_at)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;


START TRANSACTION;

-- Bir alıcı için ilk pending işi kap
  SELECT id, file_hash
  FROM offline_recipients
  WHERE recipient_id = ? AND status = 0
  ORDER BY created_at
  LIMIT 1
  FOR UPDATE SKIP LOCKED;

-- kayıt varsa:
  UPDATE offline_recipients
  SET status = 1, delivery_attempts = delivery_attempts + 1
  WHERE id = ?;

  COMMIT;

-- gönderildi bilgisi  
  UPDATE offline_recipients
  SET status = 2  -- delivered
  WHERE id = ? AND status = 1;

-- görüldü bilgisi
  UPDATE offline_recipients
  SET status = 3  -- seen
  WHERE id = ? AND status IN (1,2);



