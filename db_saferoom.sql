CREATE TABLE dosyalar (
    id INT AUTO_INCREMENT PRIMARY KEY,
    dosya_adi VARCHAR(255),
    dosya_verisi LONGBLOB,
    yuklenme_tarihi DATETIME DEFAULT CURRENT_TIMESTAMP
);
