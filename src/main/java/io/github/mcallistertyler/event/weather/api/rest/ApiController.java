package io.github.mcallistertyler.event.weather.api.rest;

import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.ApiForecastResponse;
import io.github.mcallistertyler.event.weather.api.domain.MetForcecastResponse;
import io.github.mcallistertyler.event.weather.api.service.MetForcecastService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    public ResponseEntity<ApiForecastResponse> getForecast(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDateTime
    ) {
        Coordinates coordinates = new Coordinates(lat, lon);
        if (!isWithinNextWeek(startDateTime)) {
            log.error("Request is not within the next 7 days");
            return ResponseEntity.status(400).body(new ApiForecastResponse(Collections.emptyList(), "Request is not within the next 7 days", 400));
        }
        Optional<MetForcecastResponse> forecastResponseOptional = metForcecastService.getForecast(coordinates);
        if (forecastResponseOptional.isPresent()) {
            MetForcecastResponse metForcecastResponse = forecastResponseOptional.get();
            ApiForecastResponse apiForecastResponse = createApiResponse(metForcecastResponse, startDateTime, endDateTime);
            if (!apiForecastResponse.timeSeries().isEmpty()) {
                return ResponseEntity.ok(apiForecastResponse);
            }
            log.warn("No time series events were found for specified date times");
            return ResponseEntity.badRequest().body(apiForecastResponse);
        }
        log.error("No forecast was found for given lat/lon: {}/{} for start time:{} and end time: {}", lat, lon, startDateTime, endDateTime);
        return ResponseEntity.badRequest().body(new ApiForecastResponse(Collections.emptyList(), "No forecast found for given lat/lon values", 404));
    }

    private ApiForecastResponse createApiResponse(MetForcecastResponse metForcecastResponse, Instant startDateTime, Instant endDateTime) {
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
