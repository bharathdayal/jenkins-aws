package com.example.coding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restclient(){
        return RestClient.builder().baseUrl("https://rickandmortyapi.com/").build();
    }
}
