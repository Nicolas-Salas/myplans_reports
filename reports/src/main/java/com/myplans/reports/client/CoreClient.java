package com.myplans.reports.client;

import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.dto.TagDTO;
import com.myplans.reports.exception.ResourceNotFoundException;
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
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${reports.core.uri}")
    private String coreUri;

    @Value("${reports.core.internal-token}")
    private String internalToken;

    @Value("${reports.core.timeout-ms:5000}")
    private long timeoutMs;

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
            factory.setReadTimeout(Duration.ofMillis(timeoutMs));

            restClient = RestClient.builder()
                    .baseUrl(coreUri)
                    .requestFactory(factory)
                    .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return restClient;
    }

    public PlanoDTO getPlano(Integer idPlano) {
        try {
            return client()
                    .get()
                    .uri("/api/v1/planos/{id}", idPlano)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            throw new ResourceNotFoundException("Plano no encontrado con ID: " + idPlano);
                        }
                        throw new UpstreamServiceException(
                                "Core respondió " + resp.getStatusCode() + " al consultar plano " + idPlano);
                    })
                    .body(PlanoDTO.class);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Error consultando plano {} al Core: {}", idPlano, ex.getMessage());
            throw new UpstreamServiceException("Core Service no disponible", ex);
        }
    }

    public List<TagDTO> getTagsByPlano(Integer idPlano) {
        try {
            List<TagDTO> tags = client()
                    .get()
                    .uri("/api/v1/planos/{id}/tags", idPlano)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            throw new ResourceNotFoundException("Plano no encontrado con ID: " + idPlano);
                        }
                        throw new UpstreamServiceException(
                                "Core respondió " + resp.getStatusCode() + " al consultar TAGs del plano " + idPlano);
                    })
                    .body(new ParameterizedTypeReference<List<TagDTO>>() {});
            return tags == null ? List.of() : tags;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Error consultando TAGs del plano {} al Core: {}", idPlano, ex.getMessage());
            throw new UpstreamServiceException("Core Service no disponible", ex);
        }
    }
}
