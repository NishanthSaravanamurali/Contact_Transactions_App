package com.contacttx.userservice.service;

import com.contacttx.userservice.security.InternalServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TransactionWalletClientTest {

    @Test
    void sendsUserIdAndInternalTokenToTheWalletEndpoint() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();

        InternalServiceProperties internalProperties = new InternalServiceProperties();
        internalProperties.setServiceToken("test-internal-token");
        TransactionWalletClient client = new TransactionWalletClient(
                restClientBuilder,
                internalProperties,
                "http://TRANSACTIONMICROSERVICE");

        server.expect(requestTo(
                        "http://TRANSACTIONMICROSERVICE/internal/v1/wallets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "X-Internal-Service-Token",
                        "test-internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"userId\":42}"))
                .andRespond(withSuccess());

        client.createWallet(42L);

        server.verify();
    }
}
