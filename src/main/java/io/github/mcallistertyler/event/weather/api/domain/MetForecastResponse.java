package io.github.mcallistertyler.event.weather.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetForecastResponse(
        Instant updatedAt,
        String lastModifiedHeader,
        String expiresHeader,
        List<WeatherData> weatherDataList
) {

    private static final Logger log = LoggerFactory.getLogger(MetForecastResponse.class);

    public static Optional<MetForecastResponse> parseMetResponse(JsonNode jsonNode,
                                                                 String lastModified,
                                                                 String expires) {
        JsonNode updatedAtString = jsonNode.at("/properties/meta/updated_at");
        ArrayNode timeSeriesArrayNode = jsonNode.withArray("/properties/timeseries");
        if (updatedAtString.isMissingNode() || timeSeriesArrayNode.isMissingNode()) {
            log.error("Expected nodes not found from met api response. Logging JSON we received {}", jsonNode.toPrettyString());
            return Optional.empty();
        }
        Instant updatedAt = Instant.parse(updatedAtString.asText());

        JsonPointer windSpeedPath = JsonPointer.compile("/data/instant/details/wind_speed");
        JsonPointer airTemperaturePath = JsonPointer.compile("/data/instant/details/air_temperature");

        List<WeatherData> weatherDataList = new ArrayList<>(timeSeriesArrayNode.size());
        timeSeriesArrayNode.forEach(timeSeriesJsonNode -> {
            String occurrenceString = timeSeriesJsonNode.at("/time").asText();
            Instant occurrence = Instant.parse(occurrenceString);
            Double windSpeed = getWeatherDataFromPath(windSpeedPath, timeSeriesJsonNode);
            Double airTemperature = getWeatherDataFromPath(airTemperaturePath, timeSeriesJsonNode);
            weatherDataList.add(new WeatherData(occurrence, windSpeed, airTemperature));
        });
        MetForecastResponse metForecastResponse = new MetForecastResponse(updatedAt,
                lastModified,
                expires,
                weatherDataList);
        return Optional.of(metForecastResponse);
    }

    private static Double getWeatherDataFromPath(JsonPointer jsonPointer, JsonNode jsonNode) {
        JsonNode weatherDataPath = jsonNode.at(jsonPointer);
        if (weatherDataPath.isMissingNode()) {
            log.error("Could not find weather data at path {} from json node: {}", jsonPointer, jsonNode.toPrettyString());
            return null;
        }
        return weatherDataPath.asDouble();
    }

    public boolean isDataFresh() {
        Instant updatedAt = this.updatedAt();
        Instant expiresTime = httpDateHeaderToInstant(this.expiresHeader());

        boolean isWithinTwoHours = Duration.between(updatedAt, Instant.now()).toHours() < 2;
        boolean isBeforeExpiration = false;

        if (expiresTime != null) {
            isBeforeExpiration = Instant.now().isBefore(expiresTime);
        }

        if (isBeforeExpiration) {
            return true;
        } else {
            return isWithinTwoHours;
        }
    }

    private Instant httpDateHeaderToInstant(String httpDateHeader) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME
                    .parse(httpDateHeader, Instant::from);
        } catch (Exception e) {
            log.warn("Invalid header {} received in header to instant conversion", httpDateHeader);
            return null;
        }
    }

}

