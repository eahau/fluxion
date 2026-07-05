local key = KEYS[1]
local upLimited = tonumber(ARGV[1])
local cdSeconds = tonumber(ARGV[2])
local recoveryPerCd = tonumber(ARGV[3])
local ttlSeconds = tonumber(ARGV[4])
local now = tonumber(ARGV[5])

local value = redis.call('GET', key)
local used, lastAcquireTimeMs, fractionalPermitsNumerator

if value == false then
    used = 1
    lastAcquireTimeMs = now
    fractionalPermitsNumerator = 0
    redis.call('SET', key, used .. ':' .. lastAcquireTimeMs .. ':' .. fractionalPermitsNumerator, 'EX', ttlSeconds)
    return 1
end

local sep1 = string.find(value, ':', 1, true)
local sep2 = string.find(value, ':', sep1 + 1, true)
used = tonumber(string.sub(value, 1, sep1 - 1))
lastAcquireTimeMs = tonumber(string.sub(value, sep1 + 1, sep2 - 1))
fractionalPermitsNumerator = tonumber(string.sub(value, sep2 + 1))

local allowed = false
if used < upLimited then
    used = used + 1
    allowed = true
else
    local pastTimeMs = math.max(0, now - lastAcquireTimeMs)
    local cdMs = cdSeconds * 1000
    local totalNumerator = pastTimeMs * recoveryPerCd + fractionalPermitsNumerator
    local permits = math.min(upLimited, math.floor(totalNumerator / cdMs))
    if permits >= 1 then
        used = (upLimited - permits) + 1
        allowed = true
    end
    lastAcquireTimeMs = now
    fractionalPermitsNumerator = totalNumerator % cdMs
end

redis.call('SET', key, used .. ':' .. lastAcquireTimeMs .. ':' .. fractionalPermitsNumerator, 'EX', ttlSeconds)
return allowed and 1 or 0