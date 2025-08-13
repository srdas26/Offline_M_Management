-- KEYS[1] = "baglantida_mi:{alici}"
-- KEYS[2] = "offline_mesajlar:{alici}" (HASH)
-- KEYS[3] = "offline_mesajlar_idx:{alici}" (ZSET)
-- ARGV[1] = mesajId
-- ARGV[2] = msgpack(bytes)
-- ARGV[3] = timestamp (ms)
-- ARGV[4] = max_keep (ör. 1000) ya da "" (boş) => yok
-- ARGV[5] = ttl_ms (ör. 2592000000 = 30 gün) ya da "" => yok

local online = redis.call('GET', KEYS[1])
if online == 'true' then
  return 0
end

redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('ZADD', KEYS[3], ARGV[3], ARGV[1])

-- trim
if ARGV[4] ~= '' then
  local maxk = tonumber(ARGV[4])
  local cnt = redis.call('ZCARD', KEYS[3])
  if cnt > maxk then
    local over = cnt - maxk
    local olds = redis.call('ZRANGE', KEYS[3], 0, over - 1)
    for _, mid in ipairs(olds) do
      redis.call('HDEL', KEYS[2], mid)
    end
    redis.call('ZREMRANGEBYRANK', KEYS[3], 0, over - 1)
  end
end

-- TTL
if ARGV[5] ~= '' then
  local ttl = tonumber(ARGV[5])
  redis.call('PEXPIRE', KEYS[2], ttl)
  redis.call('PEXPIRE', KEYS[3], ttl)
end

return 1
