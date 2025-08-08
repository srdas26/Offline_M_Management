CREATE DATABASE IF NOT EXISTS saferoom;
USE saferoom;

SET SQL_SAFE_UPDATES = 0;
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE IF NOT EXISTS logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME,
    level VARCHAR(10),
    classname VARCHAR(50),
    message TEXT
);

SELECT * FROM users;
DELETE  FROM users where username='abkarada';
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(256) NOT NULL,
    salt VARCHAR(64) NOT NULL,          
    email VARCHAR(100) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    verification_code VARCHAR(10) DEFAULT ('empty'),
    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
SELECT * FROM users;
SET SQL_SAFE_UPDATES = 0;

DELETE FROM users;

CREATE TABLE IF NOT EXISTS login_attempts (
    username VARCHAR(50) PRIMARY KEY,
    attempt_count INT DEFAULT 0,
    last_attempt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    attempt_time TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);

select * from users;

CREATE TABLE IF NOT EXISTS blocked_users (
    username VARCHAR(50) PRIMARY KEY,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255) DEFAULT 'Too many failed attempts',
    ip_address VARCHAR(45) DEFAULT NULL,
    is_blocked BOOLEAN,
    FOREIGN KEY (username) REFERENCES users(username)
);

alter table users drop column age;

CREATE TABLE IF NOT EXISTS verification_attempts (
    username VARCHAR(50) PRIMARY KEY,
    attempts INT DEFAULT 0,
    last_attempt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);
select username as users123 from users;
CREATE TABLE IF NOT EXISTS STUN_INFO(
	username VARCHAR(50) PRIMARY KEY,
    FOREIGN KEY (username) REFERENCES users(username),
    Public_IP VARCHAR(20),
    Public_Port INTEGER
);


CREATE TABLE dosyalar (
    id INT AUTO_INCREMENT PRIMARY KEY,
    dosya_adi VARCHAR(255),
    dosya_verisi LONGBLOB,
    yuklenme_tarihi DATETIME DEFAULT CURRENT_TIMESTAMP
);
