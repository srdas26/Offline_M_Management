import sqlite3
import os
import datetime
from contextlib import contextmanager

DB_PATH = 'deneme_saferoom.db'
TEMP_MEDIA_DIR = 'temp_media'
os.makedirs(TEMP_MEDIA_DIR, exist_ok=True)

@contextmanager
def get_conn():
    conn = sqlite3.connect(DB_PATH)
    try:
        yield conn
    finally:
        conn.close()


def initialize_db():
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS mesajlar (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id TEXT,
                receiver_id TEXT,
                media_type TEXT,
                file_path TEXT,
                timestamp TEXT
            )
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS offline_media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id TEXT,
                receiver_id TEXT,
                media_type TEXT,
                file_path TEXT,
                timestamp TEXT
            )
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS aktif_kullanicilar (
                user_id TEXT PRIMARY KEY
            )
            """
        )
        conn.commit()


def kullanici_sohbette_mi(user_id: str) -> bool:
    """Return True if the user_id exists in aktif_kullanicilar table."""
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT 1 FROM aktif_kullanicilar WHERE user_id=?",
            (user_id,)
        )
        return cursor.fetchone() is not None


def add_active_user(user_id: str) -> list:
    """Add user to aktif_kullanicilar and return pending offline media."""
    
        
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT OR IGNORE INTO aktif_kullanicilar (user_id) VALUES (?)",
            (user_id,),
        )
        conn.commit()

        # Kullanıcı sohbeti açtığında bekleyen medyaları dön
    return get_offline_media(user_id)


def remove_active_user(user_id: str) -> None:
    
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "DELETE FROM aktif_kullanicilar WHERE user_id=?",
            (user_id,),
        )
        conn.commit()


def save_offline_media(sender_id: str, receiver_id: str, media_type: str, media_bytes: bytes, filename: str) -> None:
    """Save media to disk and record metadata in offline_media table."""
    file_path = os.path.join(TEMP_MEDIA_DIR, filename)
    with open(file_path, 'wb') as f:
        f.write(media_bytes)

    timestamp = datetime.datetime.utcnow().isoformat()
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO offline_media (sender_id, receiver_id, media_type, file_path, timestamp)
            VALUES (?, ?, ?, ?, ?)
            """,
            (sender_id, receiver_id, media_type, file_path, timestamp),
        )
        conn.commit()

def get_offline_media(receiver_id: str) -> list:
    """Retrieve offline media for a user.

    Returns a list of dictionaries containing the metadata and bytes for
    each media item destined for the receiver.
    """
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            SELECT id, sender_id, media_type, file_path, timestamp
            FROM offline_media
            WHERE receiver_id=?
            ORDER BY timestamp
            """,
            (receiver_id,),
        )
        rows = cursor.fetchall()

    media_items = []
    for media_id, sender_id, media_type, file_path, timestamp in rows:
        try:
            with open(file_path, "rb") as f:
                media_bytes = f.read()
        except FileNotFoundError:
            media_bytes = None
        media_items.append(
            {
                "id": media_id,
                "sender_id": sender_id,
                "receiver_id": receiver_id,
                "media_type": media_type,
                "file_path": file_path,
                "timestamp": timestamp,
                "media_bytes": media_bytes,
            }
        )
    return media_items


def mark_media_as_read(media_id: int) -> None:
    """Delete offline media from disk and database after it is read."""
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT file_path FROM offline_media WHERE id=?",
            (media_id,),
        )
        row = cursor.fetchone()
        if row is None:
            return
        file_path = row[0]
        cursor.execute("DELETE FROM offline_media WHERE id=?", (media_id,))
        conn.commit()

    if os.path.exists(file_path):
        os.remove(file_path)


if __name__ == "__main__":
    initialize_db()
    # Example usage
    # add_active_user("123")
    # if kullanici_sohbette_mi("123"):
    #     print("User is active")
    # remove_active_user("123")
