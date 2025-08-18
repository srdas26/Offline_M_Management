CREATE DATABASE IF NOT EXISTS saferoom;
USE saferoom;

-- Users tablosu
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,       -- bcrypt kullanacaksan CHAR(60)
    salt CHAR(32) NOT NULL,               -- 16 byte random salt = 32 hex karakter
    email VARCHAR(100) NOT NULL,
    profile_picture_path VARCHAR(255) DEFAULT '/uploads/profiles/default.png',
    is_verified BOOLEAN DEFAULT FALSE,
    verification_code VARCHAR(10) DEFAULT 'empty',
    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uq_username (username),
    UNIQUE KEY uq_email (email),
    INDEX idx_username (username),
    INDEX idx_email (email)
);


--  login_attempts tablosu
CREATE TABLE IF NOT EXISTS login_attempts (
    username VARCHAR(50) PRIMARY KEY,
    attempt_count INT DEFAULT 0,
    last_attempt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);

-- blocked_users tablosu
CREATE TABLE IF NOT EXISTS blocked_users_by_system (
    username VARCHAR(50) PRIMARY KEY,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255) DEFAULT 'Too many failed attempts',
    ip_address VARCHAR(45) DEFAULT NULL,
    is_blocked BOOLEAN,
    FOREIGN KEY (username) REFERENCES users(username)
);

-- verification_attempts tablosu
CREATE TABLE IF NOT EXISTS verification_attempts (
    username VARCHAR(50) PRIMARY KEY,
    attempts INT DEFAULT 0,
    last_attempt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);

--  logs tablosu
CREATE TABLE IF NOT EXISTS logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME,
    level VARCHAR(10),
    classname VARCHAR(50),
    message TEXT
);

--  dosyalar tablosu
CREATE TABLE dosyalar (
    id INT AUTO_INCREMENT PRIMARY KEY,
    dosya_adi VARCHAR(255),
    dosya_verisi LONGBLOB,
    yuklenme_tarihi DATETIME DEFAULT CURRENT_TIMESTAMP
);
