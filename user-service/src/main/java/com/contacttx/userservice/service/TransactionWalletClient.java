package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.CreateWalletRequest;
import com.contacttx.userservice.security.InternalServiceProperties;
import com.contacttx.userservice.security.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TransactionWalletClient {

    private final RestClient restClient;
    private final InternalServiceProperties internalServiceProperties;

    public TransactionWalletClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            InternalServiceProperties internalServiceProperties,
            @Value("${integration.transaction-service.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.internalServiceProperties = internalServiceProperties;
    }

    public void createWallet(Long userId) {
        restClient.post()
                .uri("/internal/v1/wallets")
                .header(
                        InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER,
                        internalServiceProperties.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateWalletRequest(userId))
                .retrieve()
                .toBodilessEntity();
    }
}
