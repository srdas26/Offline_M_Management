import redis
import msgpack
import time
import uuid


r = redis.Redis(host='192.168.1.15', port=6379, db=0)


# Lua kodu burada
lua_code = """
local list_key = KEYS[1]
local user_id  = ARGV[1]
local ids = redis.call('LRANGE', list_key, 0, -1)
local removed = {}
for _, mid in ipairs(ids) do
  local hkey = 'mesaj:' .. mid
  local alici = redis.call('HGET', hkey, 'alici')
  if alici == user_id then
    local readset = 'mesaj:' .. mid .. ':read_by'
    if redis.call('SISMEMBER', readset, user_id) == 1 then
      redis.call('LREM', list_key, 0, mid)
      redis.call('DEL', hkey)
      redis.call('DEL', readset)
      table.insert(removed, mid)
    end
  end
end
return removed
"""
script_sha = r.script_load(lua_code)






##           kullanıcı durumu
def sohbete_katil(user_id, chat_id):
    r.sadd(f"aktif_sohbetler:{user_id}", chat_id)
    r.sadd(f"sohbet_katilim:{chat_id}", user_id)
    r.sadd("aktif_kullanicilar", user_id)

def kullanici_baglandi(user_id):
    r.set(f"baglantida_mi:{user_id}", "true")

def kullanici_kapatti(user_id):
    r.set(f"baglantida_mi:{user_id}", "false")








#mesaj_bırak:
#Kullanıcının bağlantı durumuna göre mesajı offline olarak Redis'e kaydeder.
#Eğer kullanıcı online ise False döner (P2P ile iletilmeli anlamına gelir).
#Offline ise mesajı Redis kuyruğuna ekler ve True döner.

def mesaj_birak(receiver_id, gonderen_id, icerik):

    if r.get(f"baglantida_mi:{receiver_id}") == b'true':
        return False  # Kullanıcı çevrimiçi, P2P üzerinden iletilmeli

    mesaj_id = str(uuid.uuid4())
    mesaj = {
        "id": mesaj_id,
        "gonderen": gonderen_id,
        "icerik": icerik,
        "timestamp": time.time(),
        "read_by": []
    }

    key = f"offline_mesajlar:{receiver_id}"
    r.hset(key, mesaj_id, msgpack.packb(mesaj, use_bin_type=True))
    return True


##       bağlantı

def baglanti_kur(user_id):
    kullanici_baglandi(user_id)
    offline_msgs = offline_mesajlari_listele(user_id)
    if offline_msgs:
        print(f"💬 {len(offline_msgs)} offline mesaj var:")
        for m in offline_msgs:
            print(f"[{time.ctime(m['timestamp'])}] {m['gonderen']}: {m['icerik']}")
            # Okundu olarak işaretle
            offline_mesaj_okundu(user_id, m["id"])



def mesajlari_oku(chat_id, receiver_id):
    key = f"sohbet:{chat_id}"
    mesajlar = r.lrange(key, 0, -1)
    guncellenen = []

    for m in mesajlar:
        mesaj = msgpack.unpackb(m, raw=False)
        if receiver_id != mesaj["gonderen"] and receiver_id not in mesaj.get("read_by", []):
            mesaj["read_by"].append(receiver_id)
        guncellenen.append(msgpack.packb(mesaj, use_bin_type=True))
    
    r.delete(key)
    r.rpush(key, *guncellenen)

    return [msgpack.unpackb(m, raw=False) for m in guncellenen]

def offline_mesajlari_listele(user_id):
    """
    Kullanıcıya offline mesaj listesini ID'leri ile birlikte verir.
    """
    key = f"offline_mesajlar:{user_id}"
    raw_msgs = r.hgetall(key)
    return [
        msgpack.unpackb(v, raw=False)
        for v in raw_msgs.values()
    ]

def offline_mesaj_okundu(user_id, mesaj_id):
    """
    Belirtilen offline mesajı siler (okundu sayar).
    """
    key = f"offline_mesajlar:{user_id}"
    r.hdel(key, mesaj_id)





def sohbetten_cik(user_id, chat_id):
    removed_ids = r.evalsha(script_sha, 1, f"sohbet:{chat_id}", user_id)
    r.srem(f"aktif_sohbetler:{user_id}", chat_id)
    r.srem(f"sohbet_katilim:{chat_id}", user_id)
    if r.scard(f"sohbet_katilim:{chat_id}") == 0:
        r.delete(f"sohbet:{chat_id}")
    return removed_ids




def mesajlari_sil(chat_id):
    r.delete(f"sohbet:{chat_id}")


def sohbet_sil_if_herkes_cikti(chat_id, user1, user2):
    aktif1 = r.sismember(f"aktif_sohbetler:{user1}", chat_id)
    aktif2 = r.sismember(f"aktif_sohbetler:{user2}", chat_id)
    if not aktif1 and not aktif2:
        r.delete(f"sohbet:{chat_id}")


