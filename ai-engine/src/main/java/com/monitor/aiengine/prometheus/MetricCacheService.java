package com.monitor.aiengine.prometheus;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MetricCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;

    public MetricCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveSnapshot(MetricSnapshot snapshot) {
        String key = "snapshot:" + snapshot.getServiceName();
        redisTemplate.opsForValue().set(key, snapshot, 60, TimeUnit.SECONDS);
        
        // Also store individual metrics for easy threshold comparison
        snapshot.getMetrics().forEach((metricName, value) -> {
            String metricKey = "metrics:" + snapshot.getServiceName() + ":" + metricName;
            redisTemplate.opsForValue().set(metricKey, value, 60, TimeUnit.SECONDS);
        });
    }

    public MetricSnapshot getLatestSnapshot(String serviceName) {
        String key = "snapshot:" + serviceName;
        return (MetricSnapshot) redisTemplate.opsForValue().get(key);
    }
    
    public Double getMetric(String serviceName, String metricName) {
        String metricKey = "metrics:" + serviceName + ":" + metricName;
        Object val = redisTemplate.opsForValue().get(metricKey);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        } else if (val instanceof String) {
            return Double.parseDouble((String) val);
        }
        return null;
    }
}
