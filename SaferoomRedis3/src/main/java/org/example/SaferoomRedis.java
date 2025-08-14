package org.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.params.XReadParams;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import redis.clients.jedis.StreamEntryID;




public class SaferoomRedis {

    private final JedisPool pool;
    private final ObjectMapper msgpack;

    // Redis key yönetimi
    private static class Key {
        static String userActiveChats(String userId) { return "user:" + userId + ":active_chats"; }
        static String chatParticipants(String chatId) { return "chat:" + chatId + ":participants"; }
        static String activeUsers() { return "users:active"; }
        static String userConnectionStatus(String userId) { return "user:" + userId + ":is_online"; }

        // Offline mesaj stream key
        static String offlineChatStream(String userId) { return "offline_chat_stream:" + userId; }
        static String chatStream(String chatId) { return "chat:" + chatId + ":stream"; }
    }

    public SaferoomRedis(String host, int port) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(20);
        this.pool = new JedisPool(poolConfig, host, port);
        this.msgpack = new ObjectMapper(new MessagePackFactory());
    }

    /* ------------- Kullanıcı durumu ------------- */

    public void sohbeteKatil(String userId, String chatId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.sadd(Key.userActiveChats(userId), chatId);
            jedis.sadd(Key.chatParticipants(chatId), userId);
            jedis.sadd(Key.activeUsers(), userId);
        }
    }

    public void sohbettenCik(String userId, String chatId) {
        try (Jedis jedis = pool.getResource()) {
            // Pipeline ile aktif chat ve participants temizle
            var pipe = jedis.pipelined();
            pipe.srem(Key.userActiveChats(userId), chatId);
            pipe.srem(Key.chatParticipants(chatId), userId);
            pipe.sync();

            // Eğer chat boşsa, ilgili stream ve participants key’lerini sil
            if (jedis.scard(Key.chatParticipants(chatId)) == 0) {
                var cleanupPipe = jedis.pipelined();
                cleanupPipe.del(Key.chatStream(chatId));
                cleanupPipe.del(Key.chatParticipants(chatId));
                cleanupPipe.sync();
            }
        }
    }

    public void kullaniciBaglandi(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(Key.userConnectionStatus(userId), "true");
        }
    }

    public void kullaniciKapatti(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(Key.userConnectionStatus(userId));
        }
    }

    /* ------------- Mesaj bırakma (offline) ------------- */
    public boolean mesajBirak(String receiverId, String senderId, String content) {
        try (Jedis jedis = pool.getResource()) {
            String online = jedis.get(Key.userConnectionStatus(receiverId));
            if ("true".equals(online)) {
                // Online: P2P iletilecek
                return false;
            }

            // Offline: Redis'e kaydet
            Map<String, String> message = new HashMap<>();
            message.put("sender_id", senderId);
            message.put("content", content);
            message.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

            jedis.xadd(Key.offlineChatStream(receiverId), XAddParams.xAddParams(), message);
            return true;
        }
    }


    /* ------------- Offline mesajları gösterme (kullanıcı sohbete girdiğinde) ------------- */
    public List<Map<String, String>> offlineMesajlariGetir(String receiverId) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, StreamEntryID> streams = new HashMap<>();
            streams.put(Key.offlineChatStream(receiverId), new StreamEntryID("0-0"));

            // XReadParams ile doğru kullanım
            List<Map.Entry<String, List<StreamEntry>>> streamResult =
                    jedis.xread(XReadParams.xReadParams().count(500).block(0), streams);

            if (streamResult == null || streamResult.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, String>> messages = new ArrayList<>();
            for (StreamEntry entry : streamResult.get(0).getValue()) {
                Map<String, String> msg = new HashMap<>(entry.getFields());
                msg.put("id", entry.getID().toString());
                messages.add(msg);
            }
            return messages;
        }
    }
    /* ----------------- Mesaj okundu ve silme ----------------- */
    public void mesajOkundu(String receiverId, String messageId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.xdel(Key.offlineChatStream(receiverId), new StreamEntryID(messageId));
        }
    }



    /* ------------- Kullanıcı mesajı okuduğunda silme ------------- */
    public void offlineMesajOkundu(String receiverId, String messageId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.xdel(Key.offlineChatStream(receiverId), new StreamEntryID(messageId));
        }
    }

    /* ------------- Sohbetten mesajları temizleme (mesajları tamamen silmek için) ------------- */
    public void mesajlariSil(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(Key.offlineChatStream(userId));
        }
    }
}