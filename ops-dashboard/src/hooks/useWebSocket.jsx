import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import toast from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const { user } = useAuth();
  
  // Handlers
  const onNewIncidentRef = useRef(null);
  const onAiAnalysisRef = useRef(null);
  const onMetricsUpdateRef = useRef(null);

  const connect = useCallback(() => {
    if (clientRef.current) return;
    
    // We connect using SockJS and STOMP
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: {
        Authorization: user ? `Bearer ${user.token}` : '',
      },
      debug: function (str) {
        // console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = (frame) => {
      setConnected(true);
      console.log('Connected to STOMP');

      // Subscribe to New Incident Alerts
      client.subscribe('/topic/alerts', (message) => {
        const payload = JSON.parse(message.body);
        toast.error(`New Anomaly: ${payload.service} - ${payload.anomalyType}`);
        if (onNewIncidentRef.current) {
          onNewIncidentRef.current(payload);
        }
      });

      // Subscribe to AI Analysis Completions
      // In a real app we might only subscribe to the incidents we are currently viewing
      // For now, we subscribe generically or handled by specific components via passed refs
      
      // Subscribe to Metrics Refresh
      client.subscribe('/topic/metrics', (message) => {
        const payload = JSON.parse(message.body);
        if (onMetricsUpdateRef.current) {
          onMetricsUpdateRef.current(payload);
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.activate();
    clientRef.current = client;
  }, [user]);

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
      setConnected(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      connect();
    }
    return () => {
      disconnect();
    };
  }, [user, connect, disconnect]);

  // Methods to register handlers
  const registerIncidentHandler = useCallback((handler) => {
    onNewIncidentRef.current = handler;
  }, []);
  
  const registerMetricsHandler = useCallback((handler) => {
    onMetricsUpdateRef.current = handler;
  }, []);

  return { connected, registerIncidentHandler, registerMetricsHandler };
}
