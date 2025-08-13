package org.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SaferoomRedis {

    private final JedisPool pool;
    private final ObjectMapper msgpack;
    private final String luaSha;

    public SaferoomRedis(String host, int port) {
        pool = new JedisPool(new JedisPoolConfig(), host, port);
        msgpack = new ObjectMapper(new MessagePackFactory());
        // Lua script eşdeğeri
        String luaCode = """
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
        """;
        try (Jedis jedis = pool.getResource()) {
            luaSha = jedis.scriptLoad(luaCode);
        }
    }

    /* ------------- Kullanıcı durumu ------------- */

    public void sohbeteKatil(String userId, String chatId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.sadd("aktif_sohbetler:" + userId, chatId);
            jedis.sadd("sohbet_katilim:" + chatId, userId);
            jedis.sadd("aktif_kullanicilar", userId);
        }
    }

    public void kullaniciBaglandi(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("baglantida_mi:" + userId, "true");
        }
    }

    public void kullaniciKapatti(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("baglantida_mi:" + userId, "false");
        }
    }

    /* ------------- Mesaj bırakma ------------- */

    /**
     * Kullanıcı online ise false döner (P2P iletilecek).
     * Offline ise mesaj Redis'e kaydedilir ve true döner.
     */
    public boolean mesajBirak(String receiverId, String gonderenId, String icerik) {
        try (Jedis jedis = pool.getResource()) {
            String online = jedis.get("baglantida_mi:" + receiverId);
            if ("true".equals(online)) {
                return false;
            }

            String mesajId = UUID.randomUUID().toString();
            Map<String, Object> mesaj = new HashMap<>();
            mesaj.put("id", mesajId);
            mesaj.put("gonderen", gonderenId);
            mesaj.put("icerik", icerik);
            mesaj.put("timestamp", Instant.now().toEpochMilli());
            mesaj.put("read_by", new ArrayList<String>());

            byte[] packed = msgpack.writeValueAsBytes(mesaj);
            jedis.hset(("offline_mesajlar:" + receiverId).getBytes(StandardCharsets.UTF_8),
                    mesajId.getBytes(StandardCharsets.UTF_8),
                    packed);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ------------- Bağlantı kurma ------------- */

    public void baglantiKur(String userId) {
        kullaniciBaglandi(userId);
        List<Map<String, Object>> msgs = offlineMesajlariListele(userId);
        if (!msgs.isEmpty()) {
            System.out.printf("💬 %d offline mesaj var:%n", msgs.size());
            for (Map<String, Object> m : msgs) {
                long ts = (long) m.get("timestamp");
                System.out.printf("[%s] %s: %s%n",
                        Date.from(Instant.ofEpochMilli(ts)).toString(),
                        m.get("gonderen"),
                        m.get("icerik"));
                offlineMesajOkundu(userId, (String) m.get("id"));
            }
        }
    }
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mesajlariOku(String chatId, String receiverId) {
        try (Jedis jedis = pool.getResource()) {
            String key = "sohbet:" + chatId;
            List<byte[]> raw = jedis.lrange(key.getBytes(StandardCharsets.UTF_8), 0, -1);
            List<byte[]> updated = new ArrayList<>();

            for (byte[] item : raw) {
                Map<String, Object> msg = msgpack.readValue(item, Map.class);
                String gonderen = (String) msg.get("gonderen");

                Object readByObj = msg.get("read_by");
                List<String> readBy;
                if (readByObj instanceof List<?>) {
                    readBy = ((List<?>) readByObj).stream()
                            .filter(e -> e instanceof String)
                            .map(e -> (String) e)
                            .collect(Collectors.toList());
                } else {
                    readBy = new ArrayList<>();
                    msg.put("read_by", readBy);
                }

                if (!receiverId.equals(gonderen) && !readBy.contains(receiverId)) {
                    readBy.add(receiverId);
                }

                updated.add(msgpack.writeValueAsBytes(msg));
            }

            jedis.del(key);
            if (!updated.isEmpty()) {
                jedis.rpush(key.getBytes(StandardCharsets.UTF_8),
                        updated.toArray(new byte[0][]));
            }

            return updated.stream().map(b -> {
                try {
                    return msgpack.readValue(b, Map.class);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /* ------------- Offline mesajlar ------------- */

    public List<Map<String, Object>> offlineMesajlariListele(String userId) {
        try (Jedis jedis = pool.getResource()) {
            Map<byte[], byte[]> raw = jedis.hgetAll(("offline_mesajlar:" + userId).getBytes(StandardCharsets.UTF_8));
            List<Map<String, Object>> list = new ArrayList<>();
            for (byte[] val : raw.values()) {
                list.add(msgpack.readValue(val, Map.class));
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void offlineMesajOkundu(String userId, String mesajId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(("offline_mesajlar:" + userId).getBytes(StandardCharsets.UTF_8),
                    mesajId.getBytes(StandardCharsets.UTF_8));
        }
    }

    /* ------------- Sohbetten çıkma ------------- */

    public List<String> sohbettenCik(String userId, String chatId) {
        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.evalsha(luaSha, Collections.singletonList("sohbet:" + chatId),
                    Collections.singletonList(userId));
            @SuppressWarnings("unchecked")
            List<String> removed = (List<String>) result;
            jedis.srem("aktif_sohbetler:" + userId, chatId);
            jedis.srem("sohbet_katilim:" + chatId, userId);
            if (jedis.scard("sohbet_katilim:" + chatId) == 0) {
                jedis.del("sohbet:" + chatId);
            }
            return removed;
        }
    }

    /* ------------- Diğer yardımcılar ------------- */

    public void mesajlariSil(String chatId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del("sohbet:" + chatId);
        }
    }

    public void sohbetSilIfHerkesCikti(String chatId, String user1, String user2) {
        try (Jedis jedis = pool.getResource()) {
            boolean aktif1 = jedis.sismember("aktif_sohbetler:" + user1, chatId);
            boolean aktif2 = jedis.sismember("aktif_sohbetler:" + user2, chatId);
            if (!aktif1 && !aktif2) {
                jedis.del("sohbet:" + chatId);
            }
        }
    }
}
