package io.github.mcallistertyler.event.weather.api.rest;

import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.ApiForecastResponse;
import io.github.mcallistertyler.event.weather.api.domain.MetForecastResponse;
import io.github.mcallistertyler.event.weather.api.domain.WeatherData;
import io.github.mcallistertyler.event.weather.api.service.MetForecastService;
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

    private final MetForecastService metForecastService;

    public ApiController(MetForecastService metForecastService) {
        this.metForecastService = metForecastService;
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

        Optional<MetForecastResponse> forecastResponseOptional = getForecastForCoordinates(lat, lon);
        if (forecastResponseOptional.isEmpty()) {
            return emptyMetforecastResponse(lat, lon, startDateTime, endDateTime);
        }

        MetForecastResponse metForecastResponse = forecastResponseOptional.get();
        ApiForecastResponse apiForecastResponse = createCurrentTimeResponse(metForecastResponse);

        if (apiForecastResponse.weatherData().isEmpty()) {
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
        Optional<MetForecastResponse> forecastResponseOptional = getForecastForCoordinates(lat, lon);
        if (forecastResponseOptional.isEmpty()) {
            return emptyMetforecastResponse(lat, lon, startDateTime, endDateTime);
        }

        MetForecastResponse metForecastResponse = forecastResponseOptional.get();
        ApiForecastResponse apiForecastResponse = createTimeRangeResponse(metForecastResponse, startDateTime, endDateTime);

        if (apiForecastResponse.weatherData().isEmpty()) {
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

    public Optional<MetForecastResponse> getForecastForCoordinates(double lat, double lon) {
        Coordinates coordinates = new Coordinates(lat, lon);
        return metForecastService.getForecast(coordinates);
    }

    private ApiForecastResponse createCurrentTimeResponse(MetForecastResponse metForecastResponse) {
        Instant now = Instant.now();
        List<WeatherData> weatherData = metForecastResponse.weatherDataList().stream()
                .filter(timeSeries -> timeSeries.time().isAfter(now))
                .min((timeSeries1, timeSeries2) -> {
                    Duration duration1 = Duration.between(now, timeSeries1.time());
                    Duration duration2 = Duration.between(now, timeSeries2.time());
                    return  duration1.compareTo(duration2);
                }).stream().toList();
        if (weatherData.isEmpty()) {
            return new ApiForecastResponse(weatherData, "OK", 204);
        }
        return new ApiForecastResponse(weatherData, "OK", 200);
    }

    private ApiForecastResponse createTimeRangeResponse(MetForecastResponse metForecastResponse, Instant startDateTime, Instant endDateTime) {
        List<WeatherData> weatherDataBetweenEventTimes = metForecastResponse.weatherDataList()
                .stream()
                .filter(timeSeries -> timeSeries.time().compareTo(startDateTime) >= 0 && timeSeries.time().compareTo(endDateTime) <= 0)
                .toList();
        return new ApiForecastResponse(weatherDataBetweenEventTimes, "OK", 200);
    }


    private boolean isWithinNextWeek(Instant startDateTime) {
        ZoneId utc = ZoneId.of("UTC");
        LocalDate startDate = startDateTime.atZone(utc).toLocalDate();
        LocalDate today = LocalDate.now(utc);
        LocalDate sevenDaysLater = today.plusDays(7);
        return !startDate.isBefore(today) && !startDate.isAfter(sevenDaysLater);
    }
}
