import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Activity, Server, AlertTriangle, LogOut } from 'lucide-react';
import { NavLink } from 'react-router-dom';

export default function MainLayout({ children }) {
  const { user, logout } = useAuth();

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: 'var(--bg-color)' }}>
      {/* Sidebar */}
      <aside style={{ 
        width: '260px', 
        borderRight: '1px solid var(--border-color)', 
        backgroundColor: 'var(--surface-color)',
        display: 'flex',
        flexDirection: 'column'
      }}>
        <div style={{ padding: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem', borderBottom: '1px solid var(--border-color)' }}>
          <Activity color="var(--primary-color)" />
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: 'white' }}>AI Ops</h2>
        </div>
        
        <nav style={{ padding: '1.5rem 1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', flex: 1 }}>
          <NavLink 
            to="/" 
            end
            style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem 1rem',
              borderRadius: '8px',
              backgroundColor: isActive ? 'rgba(59, 130, 246, 0.1)' : 'transparent',
              color: isActive ? 'var(--primary-color)' : 'var(--text-secondary)'
            })}
          >
            <Server size={20} />
            <span style={{ fontWeight: 500 }}>Dashboard</span>
          </NavLink>
          
          <NavLink 
            to="/incidents"
            style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem 1rem',
              borderRadius: '8px',
              backgroundColor: isActive ? 'rgba(59, 130, 246, 0.1)' : 'transparent',
              color: isActive ? 'var(--primary-color)' : 'var(--text-secondary)'
            })}
          >
            <AlertTriangle size={20} />
            <span style={{ fontWeight: 500 }}>Incidents</span>
          </NavLink>
        </nav>
        
        <div style={{ padding: '1.5rem 1rem', borderTop: '1px solid var(--border-color)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem 1rem', backgroundColor: 'rgba(0,0,0,0.2)', borderRadius: '8px' }}>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Role</span>
              <span style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--success-color)' }}>{user?.role}</span>
            </div>
            <button 
              onClick={logout} 
              style={{ padding: '0.5rem', background: 'transparent', color: 'var(--text-secondary)' }}
              title="Logout"
            >
              <LogOut size={20} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <header style={{ 
          height: '64px', 
          borderBottom: '1px solid var(--border-color)', 
          display: 'flex', 
          alignItems: 'center',
          padding: '0 2rem',
          backgroundColor: 'var(--surface-color)',
          justifyContent: 'flex-end'
        }}>
          {/* Header controls could go here */}
        </header>
        
        <div style={{ flex: 1, padding: '2rem', overflowY: 'auto' }}>
          {children}
        </div>
      </main>
    </div>
  );
}
