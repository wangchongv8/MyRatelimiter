-- KEYS[1]: window key (Redis String 计数器)
-- ARGV[1]: 窗口大小（秒）
-- ARGV[2]: 每个窗口的最大请求数
-- ARGV[3]: 请求的许可数量
-- 返回: 1 允许, 0 拒绝

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- 如果 key 不存在，说明是当前窗口的首次请求。
-- SET + EX 在一次原子操作中完成计数器创建和 TTL 设置。
-- 计数器从 requested 而非 0 开始，节省一次 INCR 调用。
local current = redis.call('GET', key)
if current == false then
    redis.call('SET', key, requested, 'EX', window)
    return 1
end

-- 窗口活跃中：检查递增后是否仍在限额内。
-- 使用 INCRBY 而非 GET+SET，避免并发更新丢失计数。
current = tonumber(current)
if current + requested <= limit then
    redis.call('INCRBY', key, requested)
    return 1
end

-- 拒绝：计数器不变。窗口将在 TTL 到期后自然重置。
return 0
