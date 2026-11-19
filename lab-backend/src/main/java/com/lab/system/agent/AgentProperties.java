package com.lab.system.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lab.llm")
public class AgentProperties {
    private boolean enabled = true;
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private int timeoutSeconds = 25;
    private int maxToolRounds = 4;
}
