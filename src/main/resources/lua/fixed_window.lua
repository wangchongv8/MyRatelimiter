-- KEYS[1]: window key
-- ARGV[1]: window size in seconds
-- ARGV[2]: max requests per window
-- ARGV[3]: requested permits
-- Returns: 1 if allowed, 0 if denied

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local current = redis.call('GET', key)
if current == false then
    redis.call('SET', key, requested, 'EX', window)
    return 1
end

current = tonumber(current)
if current + requested <= limit then
    redis.call('INCRBY', key, requested)
    return 1
end

return 0
