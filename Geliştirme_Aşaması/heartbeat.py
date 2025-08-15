import json
import socket
import threading
import time
import redis


active_users={}
r = redis.Redis(host='localhost', port=6379, db=0)

#şifreli kullanıcı bilgisi
def build_heartbeat(user_id):
    return json.dumps({
        "type": "heartbeat",
        "user_id": user_id
    }).encode('utf-8')


#şifreyi çöz
def parse_message(message):
    try:
        return json.loads(message)
    except:
        return None

#bilinen kullanıcıların aktifliği 
USER_ID = "user_001"
PEERS = [("127.0.0.1", 5001), ("127.0.0.1", 5002)]  # diğer eşler
LOCAL_PORT = 5000
active_users = {}  # {'peer_002': timestamp}

def send_heartbeat():
    while True:
        msg = build_heartbeat(USER_ID)
        for peer in PEERS:
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                s.sendto(msg, peer)
            except Exception as e:
                print("Gönderim hatasi:", e)
        time.sleep(10)

#listening
def listen():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", LOCAL_PORT))
    while True:
        data, addr = sock.recvfrom(1024)
        msg = parse_message(data.decode('utf-8'))
        if msg and msg.get("type") == "heartbeat":
            sender_id = msg.get("user_id")
            active_users[sender_id] = time.time()

#aktif taraflar
def check_active_users():
    while True:
        now = time.time()
        print("--- Aktif Kişiler ---")
    
        for user_id, last_time in active_users.items():
            if now - last_time <= 15:
                r.set(f"baglantida_mi:{user_id}", "true")
            else:
                r.set(f"baglantida_mi:{user_id}", "false")
                print("aktif değil")

# Başlat
#daemon arka planda aktif olduğunu belirtsin diye
threading.Thread(target=send_heartbeat, daemon=True).start()
threading.Thread(target=listen, daemon=True).start()
threading.Thread(target=check_active_users, daemon=True).start()


#çalışsın diye var performansa etkisi yok
while True:
    time.sleep(1)
