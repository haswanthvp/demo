package com.example.logging;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Plugin(name = "ElasticsearchAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class ElasticsearchAppender extends AbstractAppender {

    private final ElasticsearchClient client;
    private final String indexName;

    protected ElasticsearchAppender(String name, PatternLayout layout, ElasticsearchClient client, String indexName) {
        super(name, null, layout, true, null);
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

        if (layout == null) {
            layout = PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n").build();
        }

        ElasticsearchClient client = ElasticsearchClientFactory.createClient(elasticsearchUrl, username, password);
        return new ElasticsearchAppender(name, layout, client, indexName);
    }

    @Override
    public void append(LogEvent event) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", event.getTimeMillis());
            logEntry.put("level", event.getLevel().toString());
            logEntry.put("loggerName", event.getLoggerName());
            logEntry.put("message", event.getMessage().getFormattedMessage());

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .document(logEntry)
            );

            client.index(request);
        } catch (Exception e) {
            LOGGER.error("Failed to send log to Elasticsearch", e);
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
