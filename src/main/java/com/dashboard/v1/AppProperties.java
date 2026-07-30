package com.dashboard.v1;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private String secretKey;

    private String domain;

    private String tokenForIPInfo;

    private String companyName;

    private String companyIdentifier;

    @PostConstruct
    public void init() {
    }
}
