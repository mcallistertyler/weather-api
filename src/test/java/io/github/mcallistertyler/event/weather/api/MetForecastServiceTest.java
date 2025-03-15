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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ResourceUtils;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetForecastServiceTest {

    @Mock
    private OkHttpClient okHttpClient;

    @Mock
    private Call call;

    @InjectMocks
    private MetForcecastService metForcecastService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(metForcecastService, "baseUrl", "test");
        ReflectionTestUtils.setField(metForcecastService, "userAgent", "testUserAgent");
    }

    @Test
    public void testSuccessfulResponse() throws IOException {
        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:" + "example-met-response.json").toURI());
        String readJson = Files.readString(resourcePath);
        String expiresValue = "Sat, 15 Mar 2025 12:20:15 GMT";
        String lastModifiedValue = "Sat, 15 Mar 2025 11:48:45 GMT";
        Request request = new Request.Builder().url("https://test").build();
        ResponseBody dummyResponseBody = ResponseBody.create(readJson, MediaType.parse("application/json"));
        Response dummyResponse = new Response.Builder()
                .request(request)
                .header("Expires", expiresValue)
                .header("Last-Modified", lastModifiedValue)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(dummyResponseBody)
                .build();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);
        Optional<MetForcecastResponse> response = metForcecastService.getForecast(new Coordinates(59.911, 10.750));
        assertTrue(response.isPresent());
        assertEquals(expiresValue, response.get().expiresHeader());
        assertEquals(lastModifiedValue, response.get().lastModifiedHeader());
    }

    @Test
    public void checkCachePopulated() throws IOException {
        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:" + "example-met-response.json").toURI());
        String readJson = Files.readString(resourcePath);
        String expiresValue = "Sat, 15 Mar 2025 12:20:15 GMT";
        String lastModifiedValue = "Sat, 15 Mar 2025 11:48:45 GMT";
        Request request = new Request.Builder().url("https://test").build();
        ResponseBody dummyResponseBody = ResponseBody.create(readJson, MediaType.parse("application/json"));
        Response dummyResponse = new Response.Builder()
                .request(request)
                .header("Expires", expiresValue)
                .header("Last-Modified", lastModifiedValue)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(dummyResponseBody)
                .build();
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
    public void fetchesExistingFromCacheWithSimilarCoordinates() throws IOException {
        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:" + "example-met-response.json").toURI());
        String readJson = Files.readString(resourcePath);
        String expiresValue = "Sat, 15 Mar 2025 12:20:15 GMT";
        String lastModifiedValue = "Sat, 15 Mar 2025 11:48:45 GMT";
        Request request = new Request.Builder().url("https://test").build();
        ResponseBody dummyResponseBody = ResponseBody.create(readJson, MediaType.parse("application/json"));
        Response dummyResponse = new Response.Builder()
                .request(request)
                .header("Expires", expiresValue)
                .header("Last-Modified", lastModifiedValue)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(dummyResponseBody)
                .build();
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
    }

    @Test
    public void fetchesNewValueIfExpired() throws IOException {
        Path resourcePath = Paths.get(ResourceUtils.getFile("classpath:" + "example-met-response.json").toURI());
        String readJson = Files.readString(resourcePath);
        String expiresValue = "Fri, 14 Mar 2025 12:20:15 GMT";
        Request request = new Request.Builder().url("https://test").build();
        ResponseBody dummyResponseBody = ResponseBody.create(readJson, MediaType.parse("application/json"));
        Response dummyResponse = new Response.Builder()
                .request(request)
                .header("Expires", expiresValue)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(dummyResponseBody)
                .build();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(dummyResponse);

        Coordinates exampleCoordinates = new Coordinates(59.911, 10.750);

        @SuppressWarnings("unchecked")
        LoadingCache<Coordinates, MetForcecastResponse> cache = (LoadingCache<Coordinates, MetForcecastResponse>) ReflectionTestUtils.getField(metForcecastService, "forecastResponseCache");

        Instant lastWeek = Instant.now().minus(2, ChronoUnit.HOURS);
        MetForcecastResponse.TimeSeries timeSeries = new MetForcecastResponse.TimeSeries(Instant.now().plus(1, ChronoUnit.HOURS), 1.0, -7.0);
        MetForcecastResponse existingCachedResponse = new MetForcecastResponse(lastWeek, "", expiresValue, List.of(timeSeries));
        cache.put(exampleCoordinates, existingCachedResponse);
        Optional<MetForcecastResponse> newResponse = metForcecastService.getForecast(exampleCoordinates);
        assertTrue(newResponse.isPresent());
        assertNotEquals(newResponse.get(), existingCachedResponse);
    }


}
