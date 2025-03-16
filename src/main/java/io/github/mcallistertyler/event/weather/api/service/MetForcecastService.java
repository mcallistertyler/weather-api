package io.github.mcallistertyler.event.weather.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.MetForcecastResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetForcecastService {

    private static final Logger log = LoggerFactory.getLogger(MetForcecastService.class);

    @Value("${api.metno.base-url}")
    private String baseUrl;

    @Value("${api.metno.user-agent}")
    private String userAgent;

    private final OkHttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final int MAX_CACHE_SIZE = 1000;

    private final LoadingCache<Coordinates, MetForcecastResponse> forecastResponseCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .recordStats()
            .expireAfterWrite(Duration.ofHours(2))
            .build(new CacheLoader<>() {
                @NotNull
                @Override
                public MetForcecastResponse load(@NotNull Coordinates coordinates) throws IOException {
                    try {
                        Optional<MetForcecastResponse> forcecastResponse = getForecastFromMetApi(coordinates, null);
                        if (forcecastResponse.isPresent()) {
                            return forcecastResponse.get();
                        } else {
                            throw new IllegalStateException("No response returned from met api for coordinates " + coordinates);
                        }
                    } catch (IOException e) {
                        log.error("Exception occurred when trying to get forecast response for coordinates: {}", coordinates, e);
                        throw new IOException("Failed to retrieve forecast response from coordinates", e);
                    }
                }
            });

    public MetForcecastService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<MetForcecastResponse> getForecast(Coordinates coordinates) {
        try {
            MetForcecastResponse cachedForceastResponse = forecastResponseCache.getIfPresent(coordinates);
            log.info("Logging cached responses {}", forecastResponseCache.asMap());
            if (cachedForceastResponse != null) {
               if (cachedForceastResponse.isDataFresh()) {
                   log.info("Returning cached response since it has not yet expired.");
                   return Optional.of(cachedForceastResponse);
               }

               log.info("Forecast has expired. New forecast will be fetched");

               String lastModified = cachedForceastResponse.lastModifiedHeader() != null ? cachedForceastResponse.lastModifiedHeader() : null;
               Optional<MetForcecastResponse> refreshedForecastResponse = getForecastFromMetApi(coordinates, lastModified);
               if (refreshedForecastResponse.isEmpty()) {
                   return Optional.of(cachedForceastResponse);
               }
               forecastResponseCache.put(coordinates, refreshedForecastResponse.get());
               return refreshedForecastResponse;
            }
            return Optional.of(forecastResponseCache.get(coordinates));
        } catch (Exception e) {
            log.error("Failed to retrieve forecast. Returning possible cached value", e);
            return Optional.ofNullable(forecastResponseCache.getIfPresent(coordinates));
        }
    }


    public Optional<MetForcecastResponse> getForecastFromMetApi(Coordinates coordinates, String ifModifiedHeader) throws IOException {
        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("https")
                .host(baseUrl)
                .addPathSegments("weatherapi/locationforecast/2.0/compact")
                .addQueryParameter("lat", String.valueOf(coordinates.getLat()))
                .addQueryParameter("lon", String.valueOf(coordinates.getLon()))
                .build();

        final Request.Builder request = new Request.Builder()
                .url(httpUrl)
                .addHeader("User-Agent", userAgent)
                .get();

        if (ifModifiedHeader != null) {
            request.addHeader("If-Modified-Since", ifModifiedHeader);
        }
        try (Response response = httpClient.newCall(request.build()).execute()) {
            switch (response.code()) {
                case 304:
                    log.info("304 received for forecast. Re-use previous forecast");
                    return Optional.empty();
                case 429:
                    log.error("Service has been marked for throttling. Consider reducing number of requests of increasing cache expiry.");
                    return Optional.empty();
                case 203:
                    log.warn("Met api has been marked as deprecated or is in beta phase. Consider consulting api.met.no documentation");
                case 200:
                    ResponseBody body = response.body();
                    String lastModifiedHeader = response.header("Last-Modified", null);
                    String expiresHeader = response.header("Expires", null);
                    if (body != null) {
                        String json = body.string();
                        JsonNode jsonNode = objectMapper.readTree(json);
                        return MetForcecastResponse.parseMetResponse(jsonNode, lastModifiedHeader, expiresHeader);
                    }
                    break;
                default:
                    log.warn("Unexpected response code: {}", response.code());
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Error when calling met weather API", e);
            throw new IOException("Error when calling met weather API", e);
        }
    }

}
