package com.monitor.aiengine.dto;

import com.monitor.aiengine.entity.AiAnalysisResult;
import com.monitor.aiengine.entity.Incident;

public class IncidentDetailDto {
    private Incident incident;
    private AiAnalysisResult analysis;

    public IncidentDetailDto(Incident incident, AiAnalysisResult analysis) {
        this.incident = incident;
        this.analysis = analysis;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public AiAnalysisResult getAnalysis() {
        return analysis;
    }

    public void setAnalysis(AiAnalysisResult analysis) {
        this.analysis = analysis;
    }
}
