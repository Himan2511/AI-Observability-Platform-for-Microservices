import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { AlertCircle, CheckCircle, Clock, ShieldAlert, Cpu } from 'lucide-react';
import toast from 'react-hot-toast';
import { format } from 'date-fns';

export default function IncidentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [incidentDto, setIncidentDto] = useState(null);
  const [loading, setLoading] = useState(true);

  // You would typically use useWebSocket here to subscribe to '/topic/incidents/{id}/analysis'
  // and update the state when the AI finishes analysis if it was still pending.

  useEffect(() => {
    const fetchIncident = async () => {
      try {
        const headers = { Authorization: `Bearer ${user.token}` };
        const res = await axios.get(`http://localhost:8080/api/incidents/${id}`, { headers });
        setIncidentDto(res.data);
      } catch (err) {
        toast.error("Failed to load incident");
      } finally {
        setLoading(false);
      }
    };
    fetchIncident();
  }, [id, user]);

  const handleResolve = async () => {
    try {
      const headers = { Authorization: `Bearer ${user.token}` };
      await axios.put(`http://localhost:8080/api/incidents/${id}/status`, { status: 'RESOLVED' }, { headers });
      toast.success("Incident marked as RESOLVED");
      
      // Navigate back to dashboard or refresh
      navigate('/');
    } catch (err) {
      toast.error("Failed to resolve incident (Are you an admin?)");
    }
  };

  if (loading) return <div style={{ padding: '2rem' }}>Loading...</div>;
  if (!incidentDto) return <div style={{ padding: '2rem' }}>Incident not found.</div>;

  const { incident, analysis } = incidentDto;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
      
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.5rem' }}>
            <h1 style={{ fontSize: '2rem', fontWeight: 600 }}>Incident #{incident.id}</h1>
            <span style={{ 
              padding: '0.25rem 0.75rem', 
              borderRadius: '20px', 
              fontSize: '0.85rem', 
              fontWeight: 500,
              backgroundColor: incident.status === 'RESOLVED' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(245, 158, 11, 0.2)', 
              color: incident.status === 'RESOLVED' ? 'var(--success-color)' : 'var(--warning-color)' 
            }}>
              {incident.status}
            </span>
            <span style={{ 
              padding: '0.25rem 0.75rem', 
              borderRadius: '20px', 
              fontSize: '0.85rem', 
              fontWeight: 500,
              backgroundColor: 'rgba(239, 68, 68, 0.2)', 
              color: 'var(--danger-color)' 
            }}>
              {incident.severity}
            </span>
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '1.1rem' }}>
            Service: <strong style={{ color: 'var(--text-primary)' }}>{incident.serviceName}</strong> | 
            Detected at: {format(new Date(incident.detectedAt), 'MMM dd, yyyy HH:mm:ss')}
          </p>
        </div>
        
        {user.role === 'ADMIN' && incident.status !== 'RESOLVED' && (
          <button onClick={handleResolve} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', backgroundColor: 'var(--success-color)' }}>
            <CheckCircle size={18} /> Resolve Incident
          </button>
        )}
      </div>

      {/* Incident Core Info */}
      <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <AlertCircle size={20} color="var(--warning-color)" /> Trigger Details
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Anomaly Type</p>
            <p style={{ fontSize: '1.25rem', fontWeight: 500 }}>{incident.anomalyType}</p>
          </div>
          <div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Metric Value at Trigger</p>
            <p style={{ fontSize: '1.25rem', fontWeight: 500 }}>{incident.metricValue}</p>
          </div>
        </div>
      </div>

      {/* AI Analysis Section */}
      <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '2rem' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem' }}>
          <Cpu size={24} color="var(--primary-color)" /> Gemini AI Analysis
        </h2>
        
        {!analysis ? (
          <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
            <p>AI Analysis is currently processing or unavailable for this incident.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            
            <div className="glass-panel" style={{ padding: '1.5rem', borderLeft: '4px solid var(--danger-color)' }}>
              <h3 style={{ fontSize: '1.1rem', color: 'var(--danger-color)', marginBottom: '0.5rem', fontWeight: 600 }}>Root Cause</h3>
              <p style={{ color: 'var(--text-primary)', lineHeight: 1.6 }}>{analysis.rootCause}</p>
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
              <div className="glass-panel" style={{ padding: '1.5rem' }}>
                <h3 style={{ fontSize: '1.1rem', color: 'var(--primary-color)', marginBottom: '1rem', fontWeight: 600 }}>Recommended Action</h3>
                <p style={{ color: 'var(--text-primary)', lineHeight: 1.6 }}>{analysis.recommendedAction}</p>
              </div>
              
              <div className="glass-panel" style={{ padding: '1.5rem' }}>
                <h3 style={{ fontSize: '1.1rem', color: 'var(--success-color)', marginBottom: '1rem', fontWeight: 600 }}>Estimated Recovery</h3>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', color: 'var(--text-primary)', fontSize: '1.25rem', fontWeight: 500 }}>
                  <Clock size={24} color="var(--success-color)" />
                  {analysis.estimatedRecoveryTime}
                </div>
              </div>
            </div>

            <div className="glass-panel" style={{ padding: '1.5rem' }}>
              <h3 style={{ fontSize: '1.1rem', color: 'var(--warning-color)', marginBottom: '1rem', fontWeight: 600 }}>Preventive Measures</h3>
              <ul style={{ paddingLeft: '1.5rem', color: 'var(--text-primary)', lineHeight: 1.6 }}>
                {(() => {
                  try {
                    const measures = JSON.parse(analysis.preventiveMeasures || '[]');
                    return measures.map((m, i) => <li key={i}>{m}</li>);
                  } catch (e) {
                    return <li>{analysis.preventiveMeasures}</li>;
                  }
                })()}
              </ul>
            </div>

          </div>
        )}
      </div>

    </div>
  );
}
