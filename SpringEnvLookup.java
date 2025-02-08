package com.example.logging;

import org.apache.logging.log4j.core.lookup.StrLookup;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;

public class SpringEnvLookup implements StrLookup {

    private static final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringConfig.class);
    private final Environment environment;

    public SpringEnvLookup() {
        this.environment = context.getBean(Environment.class);
    }

    @Override
    public String lookup(String key) {
        return environment.getProperty(key);
    }

    @Override
    public String lookup(org.apache.logging.log4j.util.Supplier<String> key) {
        return lookup(key.get());
    }
}
