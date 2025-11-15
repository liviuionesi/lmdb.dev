package com.filmpire.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EurekaServerIntegrationTest {

    private static final String LOCALHOST_URL_PREFIX = "http://localhost:";
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_INFO_PATH = "/actuator/info";
    private static final String EUREKA_APPS_PATH = "/eureka/apps";
    private static final String ROOT_PATH = "/";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void eurekaServerIsUp() {
        String url = LOCALHOST_URL_PREFIX + port + ROOT_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Eureka");
    }

    @Test
    void actuatorHealthEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_HEALTH_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void actuatorInfoEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_INFO_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eurekaAppsEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + EUREKA_APPS_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

