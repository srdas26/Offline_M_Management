

-- remove_incoming_read_on_exit.lua
-- KEYS[1] = "sohbet:{chat_id}"
-- ARGV[1] = user_id

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
      -- listeden düş
      redis.call('LREM', list_key, 0, mid)
      -- mesaj metaverisi ve okuyanlar setini tamamen sil
      redis.call('DEL', hkey)
      redis.call('DEL', readset)
      table.insert(removed, mid)
    end
  end
end

return removed
