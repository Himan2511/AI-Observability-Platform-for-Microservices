package com.monitor.aiengine.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final JdbcTemplate jdbcTemplate;

    public RagService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Stores the generated embedding for a given incident summary.
     */
    public void storeIncidentEmbedding(Long incidentId, String summaryText, List<Double> embeddingVector) {
        try {
            // Convert List<Double> to a Postgres vector string format: "[0.1, 0.2, 0.3]"
            String vectorString = embeddingVector.toString();
            
            String sql = "INSERT INTO incident_embeddings (incident_id, summary_text, embedding) VALUES (?, ?, ?::vector)";
            jdbcTemplate.update(sql, incidentId, summaryText, vectorString);
            log.info("Stored embedding for incident ID: {}", incidentId);
        } catch (Exception e) {
            log.error("Failed to store embedding for incident {}", incidentId, e);
        }
    }

    /**
     * Retrieves top N similar past incidents using cosine similarity.
     */
    public List<String> findSimilarIncidents(List<Double> queryEmbedding, int limit) {
        String vectorString = queryEmbedding.toString();
        
        // Use PgVector cosine distance operator <=>
        // Order by distance (lower is more similar)
        String sql = """
            SELECT summary_text 
            FROM incident_embeddings 
            ORDER BY embedding <=> ?::vector 
            LIMIT ?
        """;
        
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("summary_text"), vectorString, limit);
        } catch (Exception e) {
            log.error("Failed to retrieve similar incidents from vector DB", e);
            return List.of();
        }
    }
}
