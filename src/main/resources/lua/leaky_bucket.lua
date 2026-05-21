-- KEYS[1]: bucket key (Redis Hash)
-- ARGV[1]: 容量（最大水位）
-- ARGV[2]: 漏水速率（每秒漏水请求数，如 "10.0"）
-- ARGV[3]: 请求的许可数量
-- 返回: 1 允许, 0 拒绝
--
-- 使用 redis.call('TIME') 获取 Redis 服务器时钟，避免不同应用实例间
-- 的时钟偏移问题。

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- 获取 Redis 服务器时间，转换为毫秒。
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- 读取当前桶状态。首次访问时两个字段均为 nil。
local bucket = redis.call('HMGET', key, 'water', 'last_leak_time')
local water = tonumber(bucket[1])
local last_leak = tonumber(bucket[2])

-- 首次请求：桶为空。
if water == nil then
    water = 0
    last_leak = now
end

-- 按距上次漏水的经过时间比例排水。
-- 使用浮点除法保证平滑漏水；水位不会低于 0。
local elapsed_ms = now - last_leak
if elapsed_ms > 0 then
    local leaked = (elapsed_ms / 1000.0) * rate
    water = math.max(0, water - leaked)
end

if water + requested <= capacity then
    water = water + requested
    redis.call('HMSET', key, 'water', water, 'last_leak_time', now)
    -- TTL：满桶排空所需时间 + 1s 冗余，防止时钟抖动导致提前过期。
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

-- 拒绝：不更新任何状态。下次请求从同一个 last_leak_time 重新计算
-- 经过时间，水位会正确下降。
return 0
