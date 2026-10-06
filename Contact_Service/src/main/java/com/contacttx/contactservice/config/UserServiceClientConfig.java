package com.contacttx.contactservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class UserServiceClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder userServiceRestClientBuilder(
            @Value("${contact-service.user-service.connect-timeout}") Duration connectTimeout,
            @Value("${contact-service.user-service.read-timeout}") Duration readTimeout) {

        return restClientBuilder(connectTimeout, readTimeout);
    }

    /**
     * Keeps framework clients, including Eureka, from being intercepted by the
     * load balancer.  Eureka must call its literal defaultZone URL directly.
     */
    @Bean
    @Primary
    public RestClient.Builder directRestClientBuilder(
            @Value("${contact-service.user-service.connect-timeout}") Duration connectTimeout,
            @Value("${contact-service.user-service.read-timeout}") Duration readTimeout) {

        return restClientBuilder(connectTimeout, readTimeout);
    }

    private RestClient.Builder restClientBuilder(Duration connectTimeout, Duration readTimeout) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}
