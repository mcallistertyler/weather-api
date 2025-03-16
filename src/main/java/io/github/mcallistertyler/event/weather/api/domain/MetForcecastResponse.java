package io.github.mcallistertyler.event.weather.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.mcallistertyler.event.weather.api.service.MetForcecastService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetForcecastResponse(
        Instant updatedAt,
        String lastModifiedHeader,
        String expiresHeader,
        List<TimeSeries> timeSeries
) {
    public record TimeSeries(Instant time, Double windSpeed, Double airTemperature) {}

    private static final Logger log = LoggerFactory.getLogger(MetForcecastResponse.class);

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

