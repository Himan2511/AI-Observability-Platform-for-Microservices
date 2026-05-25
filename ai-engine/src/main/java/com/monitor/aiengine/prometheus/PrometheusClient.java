package com.monitor.aiengine.prometheus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class PrometheusClient {
    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);
    
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PrometheusClient(RestTemplateBuilder restTemplateBuilder, 
                            @Value("${prometheus.base-url:http://localhost:9090}") String baseUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
    }

    public Double queryMetric(String query) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/v1/query")
                    .queryParam("query", query)
                    .toUriString();

            Map response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && "success".equals(response.get("status"))) {
                Map data = (Map) response.get("data");
                List result = (List) data.get("result");
                
                if (result != null && !result.isEmpty()) {
                    Map firstResult = (Map) result.get(0);
                    List valueObj = (List) firstResult.get("value");
                    
                    if (valueObj != null && valueObj.size() == 2) {
                        String valueStr = valueObj.get(1).toString();
                        if ("NaN".equals(valueStr)) return 0.0;
                        return Double.parseDouble(valueStr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query Prometheus: {}", query, e);
        }
        return 0.0;
    }
}
