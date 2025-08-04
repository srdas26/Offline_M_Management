import sqlite3
import os

DB_PATH = 'sohbet.db'
UPLOAD_DIR = 'uploads'
#conn = sqlite3.connect(':memory:')
def db_baglanti():
    conn = sqlite3.connect('saferoom_db.db')
    conn.execute("""
    CREATE TABLE IF NOT EXISTS mesajlar (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_id TEXT,
        gonderen TEXT,
        icerik TEXT,
        dosya_yolu TEXT,
        zaman TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        okundu INTEGER DEFAULT 0
    )
    """)
    return conn

def mesaj_ekle(chat_id, gonderen, icerik, dosya_yolu=None):
    conn = db_baglanti()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO mesajlar (chat_id, gonderen, icerik, dosya_yolu) VALUES (?, ?, ?, ?)",
        (chat_id, gonderen, icerik, dosya_yolu)
    )
    conn.commit()
    conn.close()

def sohbetten_cik(chat_id):
    conn = db_baglanti()
    cursor = conn.cursor()
    cursor.execute("SELECT dosya_yolu FROM mesajlar WHERE chat_id = ?", (chat_id,))
    dosyalar = cursor.fetchall()
    for (dosya_yolu,) in dosyalar:
        if dosya_yolu and os.path.exists(dosya_yolu):
            os.remove(dosya_yolu)
    cursor.execute("DELETE FROM mesajlar WHERE chat_id = ?", (chat_id,))
    conn.commit()
    conn.close()


