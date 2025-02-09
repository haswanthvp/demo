package com.example.logging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Plugin(name = "ElasticsearchAppender", category = "Core", elementType = "appender", printObject = true)
public class ElasticsearchAppender extends AbstractAppender {

    private final ElasticsearchClient client;
    private final String indexName;

    protected ElasticsearchAppender(String name, PatternLayout layout, ElasticsearchClient client, String indexName) {
        super(name, null, layout, true);
        this.client = client;
        this.indexName = indexName;
    }

    @PluginFactory
    public static ElasticsearchAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("elasticsearchUrl") String elasticsearchUrl,
            @PluginAttribute("indexName") String indexName,
            @PluginAttribute("username") String username,
            @PluginAttribute("password") String password,
            @PluginElement("Layout") PatternLayout layout) {

        if (name == null || elasticsearchUrl == null || indexName == null || username == null || password == null) {
            LOGGER.error("Missing required parameters for ElasticsearchAppender");
            return null;
        }

        if (layout == null) {
            layout = PatternLayout.newBuilder()
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n")
                    .build();
        }

        ElasticsearchClient client = ElasticsearchClientFactory.createClient(elasticsearchUrl, username, password);
        return new ElasticsearchAppender(name, layout, client, indexName);
    }

    @Override
    public void append(LogEvent event) {
        try {
            LOGGER.debug("Appending log event for Logstash-compatible mapping");

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

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .document(logEntry)
            );

            client.index(request);
            LOGGER.debug("Successfully sent log event to Elasticsearch with Logstash-compatible mapping.");

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
