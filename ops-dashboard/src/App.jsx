import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { useAuth } from './context/AuthContext';
import { useWebSocket } from './hooks/useWebSocket';
import Login from './pages/Login';
import MainLayout from './layouts/MainLayout';

// Placeholder for Checkpoint 2
const Dashboard = () => <div style={{ padding: '2rem' }}>Dashboard coming soon...</div>;

const PrivateRoute = ({ children }) => {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
};

const AppContent = () => {
  // Initialize websocket globally so toasts work everywhere
  useWebSocket();

  return (
    <Router>
      <Toaster position="top-right" toastOptions={{
        style: {
          background: 'var(--surface-color)',
          color: 'var(--text-primary)',
          border: '1px solid var(--border-color)',
        },
      }} />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route 
          path="/*" 
          element={
            <PrivateRoute>
              <MainLayout>
                <Dashboard />
              </MainLayout>
            </PrivateRoute>
          } 
        />
      </Routes>
    </Router>
  );
};

function App() {
  return <AppContent />;
}

export default App;
