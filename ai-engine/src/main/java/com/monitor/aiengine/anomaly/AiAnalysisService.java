package com.monitor.aiengine.anomaly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.aiengine.entity.AiAnalysisResult;
import com.monitor.aiengine.entity.Incident;
import com.monitor.aiengine.prometheus.MetricCacheService;
import com.monitor.aiengine.prometheus.MetricSnapshot;
import com.monitor.aiengine.rag.RagService;
import com.monitor.aiengine.repository.AiAnalysisResultRepository;
import com.monitor.aiengine.websocket.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final ChatModel chatModel;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final MetricCacheService metricCacheService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final EmbeddingModel embeddingModel;
    private final NotificationService notificationService;

    public AiAnalysisService(ChatModel chatModel, 
                             AiAnalysisResultRepository aiAnalysisResultRepository, 
                             MetricCacheService metricCacheService,
                             RagService ragService,
                             ObjectMapper objectMapper,
                             EmbeddingModel embeddingModel,
                             NotificationService notificationService) {
        this.chatModel = chatModel;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.metricCacheService = metricCacheService;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
        this.notificationService = notificationService;
    }

    @Async
    public void analyzeIncidentAsync(Incident incident) {
        log.info("Starting AI analysis for incident {}", incident.getId());
        
        try {
            // 1. Gather live metrics context
            MetricSnapshot snapshot = metricCacheService.getLatestSnapshot(incident.getServiceName());
            String metricsContext = snapshot != null ? snapshot.getMetrics().toString() : "No live metrics available";

            // 2. Query RAG for similar past incidents
            String summaryTextForEmbedding = String.format("Incident on %s: %s at %s", 
                    incident.getServiceName(), incident.getAnomalyType(), incident.getMetricValue());
            List<Double> queryEmbedding = embeddingModel.embed(summaryTextForEmbedding);
            
            List<String> similarIncidents = ragService.findSimilarIncidents(queryEmbedding, 3);
            String ragContext = similarIncidents.isEmpty() 
                ? "No similar past incidents found in memory."
                : String.join("\n- ", similarIncidents);

            // 3. Construct Prompt
            String template = """
                System: You are a senior site reliability engineer (SRE) with expertise in Java microservices.
                Analyze the following incident and respond in JSON format ONLY. Do not use markdown backticks for the JSON block, just return raw JSON.
                
                Incident ID: {incidentId}
                Service: {serviceName}
                Anomaly Type: {anomalyType}
                Trigger Metric Value: {metricValue}
                
                Current Live Metrics Context:
                {metricsContext}
                
                Similar past incidents from knowledge base (RAG):
                {ragContext}
                
                Respond with the following JSON structure:
                {
                  "rootCause": "Explanation of what likely caused this",
                  "contributingFactors": ["factor 1", "factor 2"],
                  "recommendedAction": "Immediate steps to take",
                  "preventiveMeasures": ["measure 1", "measure 2"],
                  "estimatedRecoveryTime": "e.g., 5 minutes"
                }
                """;

            PromptTemplate promptTemplate = new PromptTemplate(template);
            Prompt prompt = promptTemplate.create(Map.of(
                    "incidentId", incident.getId(),
                    "serviceName", incident.getServiceName(),
                    "anomalyType", incident.getAnomalyType(),
                    "metricValue", incident.getMetricValue() != null ? incident.getMetricValue() : "N/A",
                    "metricsContext", metricsContext,
                    "ragContext", ragContext
            ));

            // 4. Call Gemini
            ChatResponse response = chatModel.call(prompt);
            String rawJson = response.getResult().getOutput().getContent();
            
            // Clean markdown if Gemini returned it wrapped in ```json
            if (rawJson.startsWith("```json")) {
                rawJson = rawJson.substring(7);
            }
            if (rawJson.endsWith("```")) {
                rawJson = rawJson.substring(0, rawJson.length() - 3);
            }
            if (rawJson.startsWith("```")) {
                rawJson = rawJson.substring(3);
            }

            // 5. Parse and Save Result
            JsonNode jsonNode = objectMapper.readTree(rawJson.trim());
            
            AiAnalysisResult result = new AiAnalysisResult();
            result.setIncidentId(incident.getId());
            result.setRootCause(jsonNode.path("rootCause").asText());
            result.setRecommendedAction(jsonNode.path("recommendedAction").asText());
            result.setEstimatedRecoveryTime(jsonNode.path("estimatedRecoveryTime").asText());
            
            // Store lists as JSON strings
            result.setContributingFactors(jsonNode.path("contributingFactors").toString());
            result.setPreventiveMeasures(jsonNode.path("preventiveMeasures").toString());
            
            result.setModelUsed(response.getResult().getMetadata().getFinishReason());
            result.setSimilarIncidentsFound(0);

            aiAnalysisResultRepository.save(result);
            log.info("Successfully completed AI analysis for incident {}", incident.getId());
            
            // Push Notification
            notificationService.notifyAiAnalysisComplete(incident, result);
            
        } catch (Exception e) {
            log.error("Failed to analyze incident {} with AI", incident.getId(), e);
        }
    }
}
