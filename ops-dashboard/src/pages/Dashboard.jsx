import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Activity, AlertTriangle, Server, CheckCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';

export default function Dashboard() {
  const { user } = useAuth();
  const { registerMetricsHandler, registerIncidentHandler } = useWebSocket();
  const [services, setServices] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [liveMetrics, setLiveMetrics] = useState({});

  useEffect(() => {
    // Fetch initial data
    const fetchData = async () => {
      try {
        const headers = { Authorization: `Bearer ${user.token}` };
        
        const svcRes = await axios.get('http://localhost:8080/api/services', { headers });
        setServices(svcRes.data);
        
        const incRes = await axios.get('http://localhost:8080/api/incidents', { headers });
        // Assume pageable response, grab content
        setIncidents(incRes.data.content || []);
        
        // Fetch latest metrics snapshot for each service
        const metricsMap = {};
        for (const svc of svcRes.data) {
          try {
            const metRes = await axios.get(`http://localhost:8080/api/services/${svc.name}/metrics`, { headers });
            metricsMap[svc.name] = metRes.data;
          } catch(e) {
            console.log("No metrics yet for", svc.name);
          }
        }
        setLiveMetrics(metricsMap);
      } catch (err) {
        console.error("Failed to fetch initial data", err);
      }
    };
    fetchData();
  }, [user]);

  // Hook up websockets
  useEffect(() => {
    registerMetricsHandler((payload) => {
      // Assuming payload is { serviceName: string, metrics: Object, timestamp: number }
      setLiveMetrics(prev => ({
        ...prev,
        [payload.serviceName]: payload
      }));
    });
    
    registerIncidentHandler((newIncident) => {
      setIncidents(prev => [newIncident, ...prev].slice(0, 10)); // Keep top 10
    });
  }, [registerMetricsHandler, registerIncidentHandler]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <h1 style={{ fontSize: '2rem', fontWeight: 600 }}>Overview</h1>
      
      {/* Top Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '1.5rem' }}>
        <StatCard 
          title="Active Services" 
          value={services.length} 
          icon={<Server color="var(--primary-color)" size={24} />} 
        />
        <StatCard 
          title="Open Incidents" 
          value={incidents.filter(i => i.status === 'OPEN').length} 
          icon={<AlertTriangle color="var(--danger-color)" size={24} />} 
        />
      </div>

      {/* Main Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '2rem' }}>
        
        {/* Service Health Metrics */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Live Service Health</h2>
          
          {services.map(svc => {
            const metrics = liveMetrics[svc.name];
            return (
              <div key={svc.id} style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem', backgroundColor: 'rgba(0,0,0,0.2)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 500, color: 'var(--primary-color)' }}>{svc.name}</h3>
                  <span style={{ fontSize: '0.85rem', color: metrics ? 'var(--success-color)' : 'var(--text-secondary)' }}>
                    {metrics ? 'Online & Polling' : 'Awaiting Metrics'}
                  </span>
                </div>
                
                {metrics && metrics.metrics && (
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                    <div style={{ padding: '1rem', background: 'var(--surface-color)', borderRadius: '6px' }}>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>CPU Usage</div>
                      <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>
                        {metrics.metrics['process.cpu.usage'] ? (metrics.metrics['process.cpu.usage'] * 100).toFixed(2) : 0}%
                      </div>
                    </div>
                    <div style={{ padding: '1rem', background: 'var(--surface-color)', borderRadius: '6px' }}>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>JVM Heap</div>
                      <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>
                        {metrics.metrics['jvm.memory.used'] ? (metrics.metrics['jvm.memory.used'] / 1024 / 1024).toFixed(0) : 0} MB
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Incident Feed */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={20} color="var(--warning-color)" /> Recent Activity
          </h2>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {incidents.length === 0 ? (
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>No recent incidents detected.</p>
            ) : (
              incidents.slice(0, 5).map(inc => (
                <Link to={`/incidents/${inc.incidentId || inc.id}`} key={inc.id || Math.random()} style={{ textDecoration: 'none' }}>
                  <div style={{ 
                    padding: '1rem', 
                    borderRadius: '8px', 
                    borderLeft: `4px solid ${inc.severity === 'CRITICAL' ? 'var(--danger-color)' : 'var(--warning-color)'}`,
                    backgroundColor: 'rgba(0,0,0,0.3)',
                    transition: 'background-color 0.2s',
                    ':hover': { backgroundColor: 'var(--surface-color)' }
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{inc.service || inc.serviceName}</span>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        {inc.detectedAt ? format(new Date(inc.detectedAt), 'HH:mm:ss') : 'Just now'}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                      {inc.anomalyType}
                    </div>
                    <div style={{ marginTop: '0.5rem', display: 'inline-block', padding: '0.25rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', backgroundColor: inc.status === 'RESOLVED' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(245, 158, 11, 0.2)', color: inc.status === 'RESOLVED' ? 'var(--success-color)' : 'var(--warning-color)' }}>
                      {inc.status}
                    </div>
                  </div>
                </Link>
              ))
            )}
          </div>
        </div>

      </div>
    </div>
  );
}

const StatCard = ({ title, value, icon }) => (
  <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
    <div>
      <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>{title}</div>
      <div style={{ fontSize: '2rem', fontWeight: 700 }}>{value}</div>
    </div>
    <div style={{ padding: '1rem', backgroundColor: 'rgba(255,255,255,0.05)', borderRadius: '12px' }}>
      {icon}
    </div>
  </div>
);
