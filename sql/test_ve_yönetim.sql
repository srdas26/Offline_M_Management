USE saferoom;

-- Güvenlik ayarlarını geçici olarak kapat
SET SQL_SAFE_UPDATES = 0;
SET FOREIGN_KEY_CHECKS = 0;

-- Tüm kullanıcıları sil
DELETE FROM users;

-- Tek bir kullanıcıyı sil
DELETE FROM users WHERE username = 'abkarada';

-- Foreign key kontrollerini tekrar aç
SET FOREIGN_KEY_CHECKS = 1;

-- Test sorguları
SELECT * FROM users;
SELECT username AS users123 FROM users;

-- Örnek ALTER komutu (age sütununu kaldırmak için)
ALTER TABLE users DROP COLUMN age;
