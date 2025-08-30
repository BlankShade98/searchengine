package searchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import searchengine.services.LemmasFinder;

import java.io.IOException;

@Configuration
public class AppConfig {
    @Bean
    public LemmasFinder lemmasFinder() throws IOException {
        return new LemmasFinder();
    }
}