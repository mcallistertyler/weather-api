package io.github.mcallistertyler.event.weather.api;

import com.google.common.cache.LoadingCache;
import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.MetForcecastResponse;
import io.github.mcallistertyler.event.weather.api.service.MetForcecastService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ResourceUtils;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetForecastServiceTest {

    @Mock
    private OkHttpClient okHttpClient;

    @Mock
    private Call call;

    @InjectMocks
    private MetForcecastService metForcecastService;

    private String exampleJsonResponse;

    @BeforeEach
    public void setUp() throws IOException {
        ReflectionTestUtils.setField(metForcecastService, "baseUrl", "test");
        ReflectionTestUtils.setField(metForcecastService, "userAgent", "testUserAgent");

        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:example-met-response.json").toURI());
        exampleJsonResponse = Files.readString(resourcePath);

    }

    private MetForcecastResponse.TimeSeries createTimeSeries(Double windSpeed, Double airTemperature) {
        return new MetForcecastResponse.TimeSeries(Instant.now(), windSpeed, airTemperature);
    }

    private String instantToHttpDateHeader(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneId.of("GMT"))
                .format(instant);
    }

    public Response createDummyUnsuccessfulResponse() {
        Request request = new Request.Builder().url("https://test").build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Mandatory parameter 'lon' missing in call to Metno::WeatherAPI::Controller::Product::try {...}")
                .body(null)
                .build();
    }

    public Response createDummySuccessResponse(String dummyResponseBody,
                                               String expires,
                                               String lastModified) {
        Request request = new Request.Builder().url("https://test").build();
        ResponseBody body = ResponseBody.create(dummyResponseBody, MediaType.parse("application/json"));
        return new Response.Builder()
                .request(request)
                .header("Expires", expires)
                .header("Last-Modified", lastModified)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();
    }

    @Test
    public void testSuccessfulResponse() throws IOException {
        String expiresValue = instantToHttpDateHeader(Instant.now().plus(1, ChronoUnit.HOURS));
        String lastModifiedValue = instantToHttpDateHeader(Instant.now().plus(30, ChronoUnit.MINUTES));
        Response dummyResponse = createDummySuccessResponse(exampleJsonResponse, expiresValue, lastModifiedValue);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);
        Optional<MetForcecastResponse> response = metForcecastService.getForecast(new Coordinates(59.911, 10.750));
        assertTrue(response.isPresent());
        assertEquals(expiresValue, response.get().expiresHeader());
        assertEquals(lastModifiedValue, response.get().lastModifiedHeader());
    }


    @Test
    public void testUnsuccessfulResponse() throws IOException {
        Response dummyResponse = createDummyUnsuccessfulResponse();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);
        Optional<MetForcecastResponse> response = metForcecastService.getForecast(new Coordinates(59.911, 10.750));
        assertEquals(Optional.empty(), response);
    }

    @Test
    public void checkCachePopulated() throws IOException {
        String expiresValue = instantToHttpDateHeader(Instant.now().plus(1, ChronoUnit.HOURS));
        String lastModifiedValue = instantToHttpDateHeader(Instant.now().plus(30, ChronoUnit.MINUTES));
        Response dummyResponse = createDummySuccessResponse(exampleJsonResponse, expiresValue, lastModifiedValue);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);
        metForcecastService.getForecast(exampleCoordinates);

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache =
                (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(
                        metForcecastService, "forecastResponseCache");
        assertTrue(cache.asMap().containsKey(exampleCoordinates));
    }



    @Test
    public void checkCacheNotPopulatedWithOptionalEmpty() throws IOException {
        Request request = new Request.Builder().url("https://test").build();
        Response dummyResponse = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Mandatory parameter 'lon' missing in call to Metno::WeatherAPI::Controller::Product::try {...}")
                .body(null)
                .build();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);
        metForcecastService.getForecast(new Coordinates(59.911, 10.750));

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache =
                (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(
                        metForcecastService, "forecastResponseCache");
        assertEquals(0L, cache.size());
    }

    @Test
    public void fetchesExistingFromCacheWithSimilarCoordinates() throws IOException {
        String expiresValue = instantToHttpDateHeader(Instant.now().plus(1, ChronoUnit.HOURS));
        String lastModifiedValue = instantToHttpDateHeader(Instant.now().plus(30, ChronoUnit.MINUTES));
        Response dummyResponse = createDummySuccessResponse(exampleJsonResponse, expiresValue, lastModifiedValue);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache = (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(metForcecastService, "forecastResponseCache");

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);
        Coordinates exampleSimilarCoordinates = new Coordinates(59.9112376427, 10.75102837);
        Optional<MetForcecastResponse> first_metForcecastResponse = metForcecastService.getForecast(exampleCoordinates);
        Optional<MetForcecastResponse> second_metForecastResponse = metForcecastService.getForecast(exampleSimilarCoordinates);

        assertEquals(first_metForcecastResponse, second_metForecastResponse);
        assertEquals(1, cache.stats().hitCount());
        verify(okHttpClient, times(1)).newCall(any());
    }

    @Test
    public void fetchesNewValueIfHeaderExpired() throws IOException {
        String expiresValue = instantToHttpDateHeader(Instant.now().minus(30, ChronoUnit.MINUTES));
        String lastModifiedValue = instantToHttpDateHeader(Instant.now().minus(1, ChronoUnit.HOURS));
        Response dummyResponse = createDummySuccessResponse(exampleJsonResponse, expiresValue, lastModifiedValue);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);
        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache = (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(metForcecastService, "forecastResponseCache");

        Instant lastWeek = Instant.now().minus(2, ChronoUnit.HOURS);
        MetForcecastResponse existingCachedResponse = new MetForcecastResponse(lastWeek, lastModifiedValue, expiresValue, List.of(createTimeSeries(5.0, 22.5)));
        cache.put(exampleCoordinates, existingCachedResponse);
        Optional<MetForcecastResponse> newResponse = metForcecastService.getForecast(exampleCoordinates);
        assertTrue(newResponse.isPresent());
        assertNotEquals(newResponse.get(), existingCachedResponse);
    }

    @Test
    public void doesNotFetchNewValueIfLessThanTwoHoursOld() {

        String expiresValue = instantToHttpDateHeader(Instant.now().minus(30, ChronoUnit.MINUTES));
        String lastModifiedValue = instantToHttpDateHeader(Instant.now().minus(1, ChronoUnit.HOURS));
        Instant updatedAt = Instant.now().minus(1, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES);

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);
        MetForcecastResponse existingCachedResponse = new MetForcecastResponse(updatedAt, lastModifiedValue, expiresValue, List.of(createTimeSeries(1.0, -9.0)));

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache = (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(metForcecastService, "forecastResponseCache");

        cache.put(exampleCoordinates, existingCachedResponse);
        metForcecastService.getForecast(exampleCoordinates);
        verify(okHttpClient, times(0)).newCall(any());
    }

    @Test
    public void doesntFetchNewValueIfExpireHeaderNotOld() {

        Instant now = Instant.now();
        String expiresValue = instantToHttpDateHeader(now.plus(1, ChronoUnit.HOURS));
        String lastModifiedValue = instantToHttpDateHeader(now.plus(30, ChronoUnit.MINUTES));
        Instant updatedAt = now.minus(30, ChronoUnit.MINUTES);

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);
        MetForcecastResponse.TimeSeries timeSeries = new MetForcecastResponse.TimeSeries(Instant.now().plus(1, ChronoUnit.HOURS), 1.0, -7.0);
        MetForcecastResponse existingCachedResponse = new MetForcecastResponse(updatedAt, lastModifiedValue, expiresValue, List.of(timeSeries));

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache = (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(metForcecastService, "forecastResponseCache");

        cache.put(exampleCoordinates, existingCachedResponse);
        metForcecastService.getForecast(exampleCoordinates);
        verify(okHttpClient, times(0)).newCall(any());
    }


}
