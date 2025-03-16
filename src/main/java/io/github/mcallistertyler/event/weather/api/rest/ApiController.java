package io.github.mcallistertyler.event.weather.api.rest;

import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.ApiForecastResponse;
import io.github.mcallistertyler.event.weather.api.domain.MetForcecastResponse;
import io.github.mcallistertyler.event.weather.api.service.MetForcecastService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/forecast")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final MetForcecastService metForcecastService;

    public ApiController(MetForcecastService metForcecastService) {
        this.metForcecastService = metForcecastService;
    }

    @GetMapping(value="")
    public ResponseEntity<ApiForecastResponse> getCurrentForecast(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDateTime
    ) {
        if (!isWithinNextWeek(startDateTime)) {
            return invalidStartDateResponse();
        }

        Optional<MetForcecastResponse> forecastResponseOptional = getForecastForCoordinates(lat, lon);
        if (forecastResponseOptional.isEmpty()) {
            return emptyMetforecastResponse(lat, lon, startDateTime, endDateTime);
        }

        MetForcecastResponse metForcecastResponse = forecastResponseOptional.get();
        ApiForecastResponse apiForecastResponse = createCurrentTimeResponse(metForcecastResponse);

        if (apiForecastResponse.timeSeries().isEmpty()) {
            return noContentResponse(lat, lon, startDateTime, endDateTime);
        }

        return ResponseEntity.ok(apiForecastResponse);
    }

    @GetMapping(value="/extended")
    public ResponseEntity<ApiForecastResponse> getForecastForTimespan(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDateTime
    ) {
        if (!isWithinNextWeek(startDateTime)) {
            return invalidStartDateResponse();
        }
        Optional<MetForcecastResponse> forecastResponseOptional = getForecastForCoordinates(lat, lon);
        if (forecastResponseOptional.isEmpty()) {
            return emptyMetforecastResponse(lat, lon, startDateTime, endDateTime);
        }

        MetForcecastResponse metForcecastResponse = forecastResponseOptional.get();
        ApiForecastResponse apiForecastResponse = createTimeRangeResponse(metForcecastResponse, startDateTime, endDateTime);

        if (apiForecastResponse.timeSeries().isEmpty()) {
            return noContentResponse(lat, lon, startDateTime, endDateTime);
        }

        return ResponseEntity.ok(apiForecastResponse);
    }

    public ResponseEntity<ApiForecastResponse> noContentResponse(double lat, double lon, Instant startDateTime, Instant endDateTime) {
        log.error("No forecast was found for given lat/lon: {}/{} for start time:{} and end time: {}", lat, lon, startDateTime, endDateTime);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<ApiForecastResponse> emptyMetforecastResponse(double lat, double lon, Instant startDateTime, Instant endDateTime) {
        log.error("Unable to retrieve response from met forecast api for given lat/lon: {}/{} for start time:{} and end time: {}", lat, lon, startDateTime, endDateTime);
        return ResponseEntity.badRequest().body(new ApiForecastResponse(Collections.emptyList(), "No forecast found for given lat/lon values", 404));
    }

    public ResponseEntity<ApiForecastResponse> invalidStartDateResponse() {
        log.error("Request is not within the next 7 days");
        return ResponseEntity.badRequest().body(new ApiForecastResponse(Collections.emptyList(), "Request is not within the next 7 days", 400));
    }

    public Optional<MetForcecastResponse> getForecastForCoordinates(double lat, double lon) {
        Coordinates coordinates = new Coordinates(lat, lon);
        return metForcecastService.getForecast(coordinates);
    }

    private ApiForecastResponse createCurrentTimeResponse(MetForcecastResponse metForcecastResponse) {
        Instant now = Instant.now();
        List<MetForcecastResponse.TimeSeries> singleTimeSeries = metForcecastResponse.timeSeries().stream()
                .filter(timeSeries -> timeSeries.time().isAfter(now))
                .min((timeSeries1, timeSeries2) -> {
                    Duration duration1 = Duration.between(now, timeSeries1.time());
                    Duration duration2 = Duration.between(now, timeSeries2.time());
                    return  duration1.compareTo(duration2);
                }).stream().toList();
        if (singleTimeSeries.isEmpty()) {
            return new ApiForecastResponse(singleTimeSeries, "OK", 204);
        }
        return new ApiForecastResponse(singleTimeSeries, "OK", 200);
    }

    private ApiForecastResponse createTimeRangeResponse(MetForcecastResponse metForcecastResponse, Instant startDateTime, Instant endDateTime) {
        List<MetForcecastResponse.TimeSeries> timeSeriesBetweenEventTimes = metForcecastResponse.timeSeries()
                .stream()
                .filter(timeSeries -> timeSeries.time().compareTo(startDateTime) >= 0 && timeSeries.time().compareTo(endDateTime) <= 0)
                .toList();
        return new ApiForecastResponse(timeSeriesBetweenEventTimes, "OK", 200);
    }


    private boolean isWithinNextWeek(Instant startDateTime) {
        ZoneId utc = ZoneId.of("UTC");
        LocalDate startDate = startDateTime.atZone(utc).toLocalDate();
        LocalDate today = LocalDate.now(utc);
        LocalDate sevenDayslater = today.plusDays(7);
        return !startDate.isBefore(today) && !startDate.isAfter(sevenDayslater);
    }
}
