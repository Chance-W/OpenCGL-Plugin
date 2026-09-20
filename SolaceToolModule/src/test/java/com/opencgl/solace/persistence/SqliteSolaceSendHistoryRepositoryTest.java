package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceSendHistoryItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSolaceSendHistoryRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void storesQueriesDeletesAndClearsSendHistory() throws Exception {
        var repository = new SqliteSolaceSendHistoryRepository(tempDir.resolve("data.db"));
        SolaceSendHistoryItem first = history("1", 1000, "BROKER_ACK", "orders/a");
        SolaceSendHistoryItem second = history("2", 2000, "NACK", "orders/b");
        repository.insert(first);
        repository.insert(second);

        assertEquals(java.util.List.of("2", "1"), repository.query().stream().map(SolaceSendHistoryItem::getId).toList());

        repository.delete("2");
        assertEquals(java.util.List.of("1"), repository.query().stream().map(SolaceSendHistoryItem::getId).toList());
        repository.clear();
        assertTrue(repository.query().isEmpty());
    }

    private SolaceSendHistoryItem history(String id, long timestamp, String status, String destination) {
        SolaceSendHistoryItem item = new SolaceSendHistoryItem();
        item.setId(id);
        item.setTimestamp(new Date(timestamp));
        item.setStatus(status);
        item.setSummary(destination);
        item.setDestination(destination);
        item.setDeliveryMode("PERSISTENT");
        item.setBody("{}");
        item.setResult("Broker ACK");
        item.setCorrelationKey("key-" + id);
        item.setDurationMillis(12);
        item.setNodeId(10L);
        return item;
    }
}
