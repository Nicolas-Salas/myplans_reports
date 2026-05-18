package com.myplans.reports.client;

import com.myplans.reports.dto.HistorialDTO;
import com.myplans.reports.exception.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${reports.audit.uri}")
    private String auditUri;

    @Value("${reports.audit.internal-token}")
    private String internalToken;

    @Value("${reports.audit.timeout-ms:5000}")
    private long timeoutMs;

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
            factory.setReadTimeout(Duration.ofMillis(timeoutMs));

            restClient = RestClient.builder()
                    .baseUrl(auditUri)
                    .requestFactory(factory)
                    .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return restClient;
    }

    public List<HistorialDTO> getHistorialPorTag(Integer idTag) {
        try {
            List<HistorialDTO> historial = client()
                    .get()
                    .uri("/api/v1/historial/tag/{id}", idTag)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        throw new UpstreamServiceException(
                                "Audit respondió " + resp.getStatusCode() + " al consultar TAG " + idTag);
                    })
                    .body(new ParameterizedTypeReference<List<HistorialDTO>>() {});
            return historial == null ? List.of() : historial;
        } catch (RestClientException ex) {
            log.error("Error consultando historial del TAG {} al Audit: {}", idTag, ex.getMessage());
            throw new UpstreamServiceException("Audit Service no disponible", ex);
        }
    }
}
