package com.uci.adapter.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uci.utils.BotService;
import com.uci.utils.CampaignService;
import io.fusionauth.client.FusionAuthClient;

import java.time.Duration;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableAutoConfiguration
public class AppConfiguration {

    @Value("${campaign.url}")
    private String campaignUrl;

    @Value("${fusionauth.url}")
    private String fusionAuthUrl;

    @Value("${fusionauth.key}")
    private String fusionAuthKey;

    @Autowired
    private Cache<Object, Object> cache;

    // Default RestTemplate
    @Bean
    @Qualifier("rest")
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    // RestTemplate with Basic Auth
    @Bean
    @Qualifier("custom")
    public RestTemplate getCustomTemplate(RestTemplateBuilder builder) {
        Credentials credentials = new UsernamePasswordCredentials("test", "abcd1234");
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        HttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .disableAuthCaching()
                .build();

        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    // JSON-enabled RestTemplate
    @Bean
    @Qualifier("json")
    public RestTemplate getJSONRestTemplate() {
        return new RestTemplateBuilder()
                .additionalMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    // FusionAuth client
    @Bean
    public FusionAuthClient getFAClient() {
        return new FusionAuthClient(fusionAuthKey, fusionAuthUrl);
    }

    // BotService using WebClient and FusionAuth
    @Bean
    public BotService getBotService() {
        WebClient webClient = WebClient.builder()
                .baseUrl(campaignUrl)
                .build();

        return new BotService(webClient, getFAClient(), cache);
    }

    // Optional: Provide default in-memory Caffeine cache if not injected externally
    @Bean
    public Cache<Object, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(1000)
                .build();
    }
}
