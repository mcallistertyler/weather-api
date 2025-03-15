package io.github.mcallistertyler.event.weather.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mcallistertyler.event.weather.api.domain.MetForcecastResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetForecastResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    public void testJsonParsing() throws IOException {
        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:" + "example-met-response.json").toURI());
        String readJson = Files.readString(resourcePath);
        JsonNode jsonNode = objectMapper.readTree(readJson);
        String lastModified = "";
        String expires = "";
        Optional<MetForcecastResponse> metForcecastResponse = MetForcecastResponse.parseMetResponse(jsonNode, lastModified, expires);
        assertTrue(metForcecastResponse.isPresent());
        assertNotNull(metForcecastResponse.get().updatedAt());
    }

}
