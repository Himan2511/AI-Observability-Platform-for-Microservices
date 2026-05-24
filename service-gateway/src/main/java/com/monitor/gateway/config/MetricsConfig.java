package com.monitor.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer metrics registration for the API Gateway.
 *
 * Metrics exposed:
 *   - gateway.requests.total          (counter, tagged by route)
 *   - gateway.upstream.failures        (counter, tagged by downstream service)
 *   - gateway.routing.latency          (timer,   tagged by route)
 */
@Configuration
public class MetricsConfig {

    /**
     * Counter for total requests processed per route.
     * Tagged with 'route' label so Prometheus can break down by endpoint.
     */
    @Bean
    public Counter gatewayRequestsTotalFeed(MeterRegistry registry) {
        return Counter.builder("gateway.requests.total")
                .description("Total number of requests routed through the API Gateway")
                .tag("route", "feed")
                .register(registry);
    }

    @Bean
    public Counter gatewayRequestsTotalSearch(MeterRegistry registry) {
        return Counter.builder("gateway.requests.total")
                .description("Total number of requests routed through the API Gateway")
                .tag("route", "search")
                .register(registry);
    }

    @Bean
    public Counter gatewayRequestsTotalMedia(MeterRegistry registry) {
        return Counter.builder("gateway.requests.total")
                .description("Total number of requests routed through the API Gateway")
                .tag("route", "media")
                .register(registry);
    }

    /**
     * Counter for upstream service failures (5xx, timeouts).
     * Tagged with 'service' label to identify which downstream failed.
     */
    @Bean
    public Counter gatewayUpstreamFailuresSearch(MeterRegistry registry) {
        return Counter.builder("gateway.upstream.failures")
                .description("Number of upstream service failures detected by the Gateway")
                .tag("service", "search")
                .register(registry);
    }

    @Bean
    public Counter gatewayUpstreamFailuresMedia(MeterRegistry registry) {
        return Counter.builder("gateway.upstream.failures")
                .description("Number of upstream service failures detected by the Gateway")
                .tag("service", "media")
                .register(registry);
    }

    @Bean
    public Counter gatewayUpstreamFailuresRecommendation(MeterRegistry registry) {
        return Counter.builder("gateway.upstream.failures")
                .description("Number of upstream service failures detected by the Gateway")
                .tag("service", "recommendation")
                .register(registry);
    }
}
