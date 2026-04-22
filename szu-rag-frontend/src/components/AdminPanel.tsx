import { useState } from 'react';
import UserManagement from './UserManagement';
import KnowledgePanel from './KnowledgePanel';

interface AdminPanelProps {
  onBack: () => void;
}

export default function AdminPanel({ onBack }: AdminPanelProps) {
  const [tab, setTab] = useState<'users' | 'docs'>('users');

  return (
    <div className="flex-1 flex flex-col min-w-0">
      {/* Top bar */}
      <div
        className="h-12 flex items-center px-3 shrink-0 gap-3"
        style={{
          background: 'var(--bg-sidebar)',
          backdropFilter: 'blur(12px)',
          borderBottom: '1px solid var(--border-light)',
        }}
      >
        <button
          onClick={onBack}
          className="w-8 h-8 flex items-center justify-center rounded-lg transition-colors duration-150"
          style={{ color: 'var(--text-secondary)' }}
          onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
          onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
        >
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M11 4L6 9L11 14" />
          </svg>
        </button>
        <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>控制面板</span>

        {/* Tab Switch */}
        <div className="ml-4 flex p-1 rounded-xl" style={{ background: 'var(--bg-tertiary)' }}>
          <button
            onClick={() => setTab('users')}
            className="px-3 py-1 text-xs font-medium rounded-lg transition-all duration-200"
            style={{
              background: tab === 'users' ? 'var(--bg-primary)' : 'transparent',
              color: tab === 'users' ? 'var(--accent)' : 'var(--text-tertiary)',
              boxShadow: tab === 'users' ? 'var(--shadow-sm)' : 'none',
            }}
          >
            用户管理
          </button>
          <button
            onClick={() => setTab('docs')}
            className="px-3 py-1 text-xs font-medium rounded-lg transition-all duration-200"
            style={{
              background: tab === 'docs' ? 'var(--bg-primary)' : 'transparent',
              color: tab === 'docs' ? 'var(--accent)' : 'var(--text-tertiary)',
              boxShadow: tab === 'docs' ? 'var(--shadow-sm)' : 'none',
            }}
          >
            文档管理
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto">
        {tab === 'users' ? <UserManagement /> : <KnowledgePanel isAdmin={true} />}
      </div>
    </div>
  );
}
