package com.anjia.unidbgserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(FQDownloadProperties downloadProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(safeToInt(downloadProperties.getUpstreamConnectTimeoutMs(), 8000));
        factory.setReadTimeout(safeToInt(downloadProperties.getUpstreamReadTimeoutMs(), 15000));
        return new RestTemplate(factory);
    }

    private int safeToInt(long value, int defaultValue) {
        if (value <= 0) {
            return defaultValue;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }
}

