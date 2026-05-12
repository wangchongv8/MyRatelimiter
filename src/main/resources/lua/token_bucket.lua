-- KEYS[1]: bucket key
-- ARGV[1]: max tokens (capacity)
-- ARGV[2]: refill rate (tokens per second)
-- ARGV[3]: requested permits
-- ARGV[4]: current time in milliseconds
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

local elapsed_ms = now - last_refill
if elapsed_ms > 0 then
    local refill = (elapsed_ms / 1000.0) * rate
    tokens = math.min(capacity, tokens + refill)
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill_time', now)
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

return 0
