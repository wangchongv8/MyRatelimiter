-- KEYS[1]: bucket key
-- ARGV[1]: capacity (max water level)
-- ARGV[2]: leak rate (requests leaked per second)
-- ARGV[3]: requested permits
-- ARGV[4]: current time in milliseconds
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'water', 'last_leak_time')
local water = tonumber(bucket[1])
local last_leak = tonumber(bucket[2])

if water == nil then
    water = 0
    last_leak = now
end

local elapsed_ms = now - last_leak
if elapsed_ms > 0 then
    local leaked = (elapsed_ms / 1000.0) * rate
    water = math.max(0, water - leaked)
end

if water + requested <= capacity then
    water = water + requested
    redis.call('HMSET', key, 'water', water, 'last_leak_time', now)
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

redis.call('HSET', key, 'last_leak_time', now)
return 0
