package org.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.StreamEntryID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class SaferoomRedis {

    private final JedisPool pool;

    private static final Logger logger = LoggerFactory.getLogger(SaferoomRedis.class);

    // Redis key yönetimi
    private static class Key {
        static String userActiveChats(String userId) { return "user:" + userId + ":active_chats"; }
        static String chatParticipants(String chatId) { return "chat:" + chatId + ":participants"; }
        static String activeUsers() { return "users:active"; }
        static String userConnectionStatus(String userId) { return "user:" + userId + ":is_online"; }
        static String offlineChatStream(String userId) { return "offline_chat_stream:" + userId; }
        static String chatStream(String chatId) { return "chat:" + chatId + ":stream"; }
        static String maintenanceLastCleanup() { return "maintenance:last_cleanup"; }
    }

    public SaferoomRedis(String host, int port) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(20);
        this.pool = new JedisPool(poolConfig, host, port);

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
            var pipe = jedis.pipelined();
            pipe.srem(Key.userActiveChats(userId), chatId);
            pipe.srem(Key.chatParticipants(chatId), userId);
            pipe.sync();

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
                return false;
            }

            Map<String, String> message = new HashMap<>();
            message.put("sender_id", senderId);
            message.put("content", content);
            message.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

            jedis.xadd(Key.offlineChatStream(receiverId), XAddParams.xAddParams(), message);
            return true;
        }
    }

    /* ------------- Offline mesajları gösterme ------------- */
    public List<Map<String, String>> offlineMesajlariGetir(String receiverId) {
        try (Jedis jedis = pool.getResource()) {

            runCleanupIfDue(jedis, receiverId);

            Map<String, StreamEntryID> streams = new HashMap<>();
            streams.put(Key.offlineChatStream(receiverId), new StreamEntryID("0-0"));

            List<Map.Entry<String, List<StreamEntry>>> streamResult =
                    jedis.xread(XReadParams.xReadParams().count(500).block(0), streams);

            if (streamResult == null || streamResult.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, String>> messages = new ArrayList<>();
            for (StreamEntry entry : streamResult.getFirst().getValue()) {
                Map<String, String> msg = new HashMap<>(entry.getFields());
                msg.put("id", entry.getID().toString());
                messages.add(msg);
            }
            return messages;
        }
    }

    public void mesajOkundu(String receiverId, String messageId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.xdel(Key.offlineChatStream(receiverId), new StreamEntryID(messageId));
        }
    }

    public void offlineMesajOkundu(String receiverId, String messageId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.xdel(Key.offlineChatStream(receiverId), new StreamEntryID(messageId));
        }
    }

    public void mesajlariSil(String userId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(Key.offlineChatStream(userId));
        }
    }

    /* ------------- Haftalık Temizlik ------------- */

    private void runCleanupIfDue(Jedis jedis, String userId) {
        long cleanupInterval = 24 * 60 * 60 * 1000L; // 24 saat
        long now = System.currentTimeMillis();

        String lastCleanupStr = jedis.hget(Key.maintenanceLastCleanup(), userId);
        long lastCleanup = (lastCleanupStr != null) ? Long.parseLong(lastCleanupStr) : 0;

        if ((now - lastCleanup) > cleanupInterval) {
            runWeeklyUnreadMessageCleanup(userId);

            jedis.hset(Key.maintenanceLastCleanup(), userId, String.valueOf(now));
        }
    }

    public void runWeeklyUnreadMessageCleanup(String userId) {
        long oneWeek = 7 * 24 * 60 * 60 * 1000L;
        long cutoffTimestamp = System.currentTimeMillis() - oneWeek;

        try (Jedis jedis = pool.getResource()) {
            String streamKey = Key.offlineChatStream(userId);

            StreamEntryID startRange = new StreamEntryID("-");
            StreamEntryID endRange = new StreamEntryID(cutoffTimestamp + "-0");

            List<StreamEntry> oldMessages = jedis.xrange(streamKey, startRange, endRange);
            if (oldMessages == null || oldMessages.isEmpty()) return;

            StreamEntryID[] idsToDelete = oldMessages.stream()
                    .map(StreamEntry::getID)
                    .toArray(StreamEntryID[]::new);

            if (idsToDelete.length > 0) {
                jedis.xdel(streamKey, idsToDelete);
            }
        }
    }

    /* ------------- Otomatik Temizlik Thread (SCAN ile) ------------- */


    public void startAutoCleanupTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable cleanupTask = () -> {
            try {
                cleanupOldOfflineMessagesScan();
            } catch (Exception e) {
                logger.error("Temizlik sırasında hata oluştu", e);
            }
        };

        // Sunucu açılır açılmaz başlat, 24 saatte bir tekrar et
        scheduler.scheduleAtFixedRate(cleanupTask, 0, 24, TimeUnit.HOURS);
    }


    public void cleanupOldOfflineMessagesScan() {
        long oneWeekMillis = 7 * 24 * 60 * 60 * 1000L;
        long cutoffTimestamp = System.currentTimeMillis() - oneWeekMillis;

        try (Jedis jedis = pool.getResource()) {
            String cursor = "0";

            do {
                var scanResult = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams()
                        .match("offline_chat_stream:*")
                        .count(100));
                cursor = scanResult.getCursor();

                for (String streamKey : scanResult.getResult()) {
                    StreamEntryID startRange = new StreamEntryID("-");
                    StreamEntryID endRange = new StreamEntryID(cutoffTimestamp + "-0");

                    List<StreamEntry> oldMessages = jedis.xrange(streamKey, startRange, endRange);

                    if (oldMessages != null && !oldMessages.isEmpty()) {
                        StreamEntryID[] idsToDelete = oldMessages.stream()
                                .map(StreamEntry::getID)
                                .toArray(StreamEntryID[]::new);

                        jedis.xdel(streamKey, idsToDelete);
                    }
                }
            } while (!cursor.equals("0"));
        }
    }
    public static void main(String[] args) {
        SaferoomRedis redis = new SaferoomRedis("localhost", 6379);

        // Sunucu açılır açılmaz otomatik temizlik thread’i başlasın
        redis.startAutoCleanupTask();


    }

}