-- KEYS[1]: sorted set key
-- ARGV[1]: window size in milliseconds
-- ARGV[2]: max requests per window
-- ARGV[3]: current time in milliseconds
-- ARGV[4]: unique log entry id
-- ARGV[5]: requested permits (added as separate entries)
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local entry_id = ARGV[4]
local requested = tonumber(ARGV[5])

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local count = redis.call('ZCARD', key)
if count + requested <= limit then
    for i = 1, requested do
        redis.call('ZADD', key, now, entry_id .. ':' .. i)
    end
    redis.call('PEXPIRE', key, window + 1000)
    return 1
end

return 0
