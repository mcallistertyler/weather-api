package io.github.mcallistertyler.event.weather.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetForcecastResponse(
        Instant updatedAt,
        String lastModifiedHeader,
        String expiresHeader,
        List<TimeSeries> timeSeries
) {
    public record TimeSeries(Instant time, Double windSpeed, Double airTemperature) {}

    public static Optional<MetForcecastResponse> parseMetResponse(JsonNode jsonNode,
                                                                  String lastModified,
                                                                  String expires) {
        JsonNode updatedAtString = jsonNode.at("/properties/meta/updated_at");
        ArrayNode timeSeriesArrayNode = jsonNode.withArray("/properties/timeseries");
        List<TimeSeries> timeSeriesList = new ArrayList<>(timeSeriesArrayNode.size());
        timeSeriesArrayNode.forEach(timeSeriesJsonNode -> {
            String occurrenceString = timeSeriesJsonNode.at("/time").asText();
            Instant occurrence = Instant.parse(occurrenceString);
            Double windSpeed = timeSeriesJsonNode.at("/data/instant/details/wind_speed").asDouble();
            Double airTemperature = timeSeriesJsonNode.at("/data/instant/details/air_temperature").asDouble();
            TimeSeries timeSeries = new TimeSeries(occurrence, windSpeed, airTemperature);
            timeSeriesList.add(timeSeries);
        });
        if (!updatedAtString.isMissingNode() && !timeSeriesArrayNode.isMissingNode()) {
            Instant updatedAt = Instant.parse(updatedAtString.asText());
            MetForcecastResponse metForcecastResponse = new MetForcecastResponse(updatedAt,
                    lastModified,
                    expires,
                    timeSeriesList);

            return Optional.of(metForcecastResponse);
        }
        return Optional.empty();
    }
}

