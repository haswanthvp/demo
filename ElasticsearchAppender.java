package com.example.logging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Plugin(name = "ElasticsearchAppender", category = "Core", elementType = "appender", printObject = true)
public class ElasticsearchAppender extends AbstractAppender {

    private final ElasticsearchClient client;
    private final String indexName;
    private static final BlockingQueue<LogEvent> logBuffer = new LinkedBlockingQueue<>();
    private static volatile boolean applicationInitialized = false;

    protected ElasticsearchAppender(String name, PatternLayout layout, ElasticsearchClient client, String indexName) {
        super(name, null, layout, true);
        this.client = client;
        this.indexName = indexName;
    }

    @PluginFactory
    public static ElasticsearchAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") PatternLayout layout) {

        if (name == null) {
            LOGGER.error("Appender name is required");
            return null;
        }

        if (layout == null) {
            layout = PatternLayout.newBuilder()
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n")
                    .build();
        }

        // Start Spring context to access environment properties
        ConfigurableApplicationContext context = SpringApplication.run(Application.class);  // Replace with your Spring Boot main class
        Environment env = context.getBean(Environment.class);

        String elasticsearchUrl = getPropertyWithRetry(env, "elasticsearch.url", 5, 1000);
        String indexName = getPropertyWithRetry(env, "elasticsearch.index", 5, 1000);
        String username = getPropertyWithRetry(env, "elasticsearch.username", 5, 1000);
        String password = getPropertyWithRetry(env, "elasticsearch.password", 5, 1000);

        ElasticsearchClient client = ElasticsearchClientFactory.createClient(elasticsearchUrl, username, password);
        return new ElasticsearchAppender(name, layout, client, indexName);
    }

    @Override
    public void append(LogEvent event) {
        if (!applicationInitialized) {
            logBuffer.add(event.toImmutable());  // Buffer the log event if application is not initialized
            return;
        }
        sendLogToElasticsearch(event);
    }

    private void sendLogToElasticsearch(LogEvent event) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("@timestamp", event.getTimeMillis());
            logEntry.put("host", getHostName());
            logEntry.put("level", event.getLevel().toString());
            logEntry.put("logger_name", event.getLoggerName());
            logEntry.put("message", event.getMessage().getFormattedMessage());
            logEntry.put("thread_name", event.getThreadName());

            if (event.getThrown() != null) {
                logEntry.put("exception", formatException(event.getThrown()));
            }

            client.index(i -> i.index(indexName).document(logEntry));
            LOGGER.debug("Log event sent to Elasticsearch.");
        } catch (Exception e) {
            LOGGER.error("Failed to send log event to Elasticsearch", e);
        }
    }

    private static String getPropertyWithRetry(Environment env, String propertyName, int maxRetries, long delayMillis) {
        for (int i = 0; i < maxRetries; i++) {
            String value = env.getProperty(propertyName);
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for property: " + propertyName, e);
            }
        }
        throw new RuntimeException("Failed to retrieve property: " + propertyName + " after " + maxRetries + " retries.");
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to get hostname", e);
            return "unknown-host";
        }
    }

    private Map<String, Object> formatException(Throwable throwable) {
        Map<String, Object> exception = new HashMap<>();
        exception.put("type", throwable.getClass().getName());
        exception.put("message", throwable.getMessage());
        exception.put("stack_trace", getStackTraceAsString(throwable));
        return exception;
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static void setApplicationInitialized(boolean initialized) {
        applicationInitialized = initialized;
        if (initialized) {
            flushBufferedLogs();
        }
    }

    private static void flushBufferedLogs() {
        LogEvent event;
        while ((event = logBuffer.poll()) != null) {
            LOGGER.debug("Flushing buffered log event.");
            // Reuse the existing Elasticsearch client and send logs
        }
    }

    @Override
    public void stop() {
        try {
            client._transport().close();
        } catch (Exception e) {
            LOGGER.error("Failed to close Elasticsearch client", e);
        }
        super.stop();
    }
}
