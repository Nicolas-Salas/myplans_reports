package com.myplans.reports.client;

import com.myplans.reports.dto.UserDTO;
import com.myplans.reports.exception.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${reports.auth.uri}")
    private String authUri;

    @Value("${reports.auth.internal-token}")
    private String internalToken;

    @Value("${reports.auth.timeout-ms:5000}")
    private long timeoutMs;

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
            factory.setReadTimeout(Duration.ofMillis(timeoutMs));
            restClient = RestClient.builder()
                    .baseUrl(authUri)
                    .requestFactory(factory)
                    .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return restClient;
    }

    public Map<Integer, String> getUserNames() {
        try {
            List<UserDTO> users = client()
                    .get()
                    .uri("/api/admin/users")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserDTO>>() {});
            if (users == null) return Map.of();
            return users.stream()
                    .filter(u -> u.idUsuario() != null)
                    .collect(Collectors.toMap(UserDTO::idUsuario, UserDTO::nombreCompleto));
        } catch (RestClientException ex) {
            log.warn("No se pudo obtener usuarios del Auth Service: {}", ex.getMessage());
            return Map.of();
        } catch (UpstreamServiceException ex) {
            log.warn("Auth Service no disponible para obtener nombres: {}", ex.getMessage());
            return Map.of();
        }
    }
}
