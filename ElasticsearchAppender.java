package com.example.logging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;
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

    private static final BlockingQueue<LogEvent> logBuffer = new LinkedBlockingQueue<>();
    private static volatile boolean applicationInitialized = false;
    private static ElasticsearchClient client;
    private static String indexName;

    protected ElasticsearchAppender(String name, PatternLayout layout) {
        super(name, null, layout, true);
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

        return new ElasticsearchAppender(name, layout);
    }

    @Override
    public void append(LogEvent event) {
        if (!applicationInitialized) {
            logBuffer.add(event.toImmutable());  // Buffer the log event until application is initialized
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

    public static void initializeClient(ConfigurableApplicationContext context) {
        Environment env = context.getBean(Environment.class);
        
        // Fetch properties from the Spring environment
        String elasticsearchUrl = env.getProperty("elasticsearch.url");
        indexName = env.getProperty("elasticsearch.index");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");

        if (elasticsearchUrl == null || indexName == null || username == null || password == null) {
            throw new IllegalStateException("Elasticsearch configuration is incomplete");
        }

        client = ElasticsearchClientFactory.createClient(elasticsearchUrl, username, password);
        applicationInitialized = true;
        flushBufferedLogs();
    }

    private static void flushBufferedLogs() {
        LogEvent event;
        while ((event = logBuffer.poll()) != null) {
            LOGGER.debug("Flushing buffered log event: {}", event.getMessage().getFormattedMessage());
            try {
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("@timestamp", event.getTimeMillis());
                logEntry.put("level", event.getLevel().toString());
                logEntry.put("logger_name", event.getLoggerName());
                logEntry.put("message", event.getMessage().getFormattedMessage());
                client.index(i -> i.index(indexName).document(logEntry));
            } catch (Exception e) {
                LOGGER.error("Failed to flush buffered log event", e);
            }
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
