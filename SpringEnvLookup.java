package com.example.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public class SpringEnvLookup implements StrLookup {

    private static ConfigurableApplicationContext context;
    private final Environment environment;

    public SpringEnvLookup() {
        if (context == null) {
            context = SpringApplication.run(Application.class);  // Replace with your main Spring Boot application class
        }
        this.environment = context.getBean(Environment.class);
    }

    @Override
    public String lookup(String key) {
        return environment.getProperty(key);
    }

    @Override
    public String lookup(LogEvent event, String key) {
        return lookup(key);  // Ignore the event and delegate to the other lookup method
    }
}
