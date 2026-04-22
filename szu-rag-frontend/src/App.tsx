import { useEffect, useState } from 'react';
import { useChatStore } from './store/chatStore';
import { useAuthStore } from './store/authStore';
import * as api from './api/chat';
import * as authApi from './api/auth';
import ChatWindow from './components/ChatWindow';
import CampusCalendarWidget from './components/CampusCalendarWidget';
import RoleSelector from './components/RoleSelector';
import LoginPage from './components/LoginPage';
import AdminPanel from './components/AdminPanel';
import type { CalendarContext } from './api/chat';

type ViewMode = 'chat' | 'admin';

const SUGGESTIONS = [
  { icon: '📖', text: '深大有哪些热门专业？' },
  { icon: '📅', text: '秋季学期校历是什么？' },
  { icon: '🏢', text: '图书馆开放时间是怎样的？' },
  { icon: '🎓', text: '研究生保研条件是什么？' },
];

function getDynamicSuggestions(calendar: CalendarContext | null) {
  if (!calendar || !calendar.upcomingEvents) return SUGGESTIONS;
  const dynamic: { icon: string; text: string }[] = [];
  for (const evt of calendar.upcomingEvents) {
    if (evt.eventType === 'enrollment' && dynamic.length < 2) {
      dynamic.push({ icon: '📚', text: '选课怎么操作？有什么注意事项？' });
    }
    if (evt.eventType === 'exam' && dynamic.length < 3) {
      dynamic.push({ icon: '📝', text: '考试安排在哪里查？' });
    }
    if (evt.eventType === 'holiday' && dynamic.length < 2) {
      dynamic.push({ icon: '🏖', text: '放假时间是什么时候？' });
    }
  }
  while (dynamic.length < 4) {
    dynamic.push(SUGGESTIONS[dynamic.length] || SUGGESTIONS[0]);
  }
  return dynamic;
}

export default function App() {
  const { conversations, currentConvId, setConversations, setCurrentConv } = useChatStore();
  const { user, isAuthenticated, isAdmin, initialized, setAuth, clearAuth, setInitialized } = useAuthStore();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [view, setView] = useState<ViewMode>('chat');
  const [calendar, setCalendar] = useState<CalendarContext | null>(null);
  const [showUserMenu, setShowUserMenu] = useState(false);

  // Auth initialization
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      authApi.getCurrentUser().then((u) => {
        setAuth(u, token);
        setInitialized(true);
      }).catch(() => {
        clearAuth();
        setInitialized(true);
      });
    } else {
      setInitialized(true);
    }
  }, []);

  // Load data after auth
  useEffect(() => {
    if (isAuthenticated) {
      loadConversations();
      api.getCalendarContext().then(setCalendar);
    }
  }, [isAuthenticated]);

  const loadConversations = async () => {
    const convs = await api.listConversations();
    setConversations(convs);
  };

  const handleNewChat = async () => {
    const conv = await api.createConversation();
    setConversations([conv, ...conversations]);
    setCurrentConv(conv.id);
  };

  const handleDelete = async (id: string) => {
    await api.deleteConversation(id);
    if (currentConvId === id) setCurrentConv(null);
    loadConversations();
  };

  const handleSuggestionClick = async (_text: string) => {
    if (!currentConvId) {
      const conv = await api.createConversation();
      setConversations([conv, ...conversations]);
      setCurrentConv(conv.id);
    }
  };

  const handleLogout = async () => {
    await authApi.logout();
    clearAuth();
    setShowUserMenu(false);
  };

  // Not initialized yet — show loading
  if (!initialized) {
    return (
      <div className="flex items-center justify-center h-screen" style={{ background: 'var(--bg-secondary)' }}>
        <div className="flex gap-1.5">
          {[0, 1, 2].map((i) => (
            <div key={i} className="w-2 h-2 rounded-full animate-pulse-dot" style={{ background: 'var(--accent)', animationDelay: `${i * 0.15}s` }} />
          ))}
        </div>
      </div>
    );
  }

  // Not authenticated — show login
  if (!isAuthenticated) {
    return <LoginPage />;
  }

  return (
    <div className="flex h-screen" style={{ background: 'var(--bg-secondary)' }}>
      {/* Sidebar */}
      <div
        className={`${sidebarOpen ? 'w-[260px]' : 'w-0'} transition-all duration-300 ease-out overflow-hidden flex flex-col relative`}
        style={{
          background: 'var(--bg-sidebar)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          borderRight: '1px solid var(--border-light)',
        }}
      >
        {/* Logo area */}
        <div className="px-4 pt-4 pb-2">
          <div className="flex items-center gap-2.5">
            <div
              className="w-8 h-8 rounded-xl flex items-center justify-center text-white text-sm font-bold shrink-0"
              style={{ background: 'var(--accent-gradient)' }}
            >
              S
            </div>
            <div className="min-w-0">
              <div className="text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                深大智答
              </div>
              <div className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                SZU-RAG
              </div>
            </div>
          </div>
        </div>

        {/* Admin sidebar */}
        {view === 'admin' && (
          <div className="px-3 py-2">
            <div className="px-3 py-2 text-xs font-medium rounded-lg" style={{ background: 'var(--accent-light)', color: 'var(--accent)' }}>
              控制面板
            </div>
          </div>
        )}

        {/* Chat Sidebar Content */}
        {view === 'chat' && (
          <>
            <div className="px-3 pb-2">
              <button
                onClick={handleNewChat}
                className="w-full py-2 px-3 rounded-xl text-white text-sm font-medium flex items-center justify-center gap-1.5 transition-all duration-200 hover:opacity-90 active:scale-[0.98]"
                style={{ background: 'var(--accent-gradient)' }}
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                  <line x1="8" y1="3" x2="8" y2="13" /><line x1="3" y1="8" x2="13" y2="8" />
                </svg>
                新对话
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-2 pb-2 space-y-0.5">
              {conversations.map((conv) => (
                <div
                  key={conv.id}
                  className="flex items-center justify-between px-3 py-2 rounded-xl cursor-pointer text-sm group transition-all duration-150"
                  style={{
                    background: currentConvId === conv.id ? 'var(--accent-light)' : 'transparent',
                    color: currentConvId === conv.id ? 'var(--accent)' : 'var(--text-secondary)',
                  }}
                  onClick={() => setCurrentConv(conv.id)}
                  onMouseEnter={(e) => {
                    if (currentConvId !== conv.id) e.currentTarget.style.background = 'var(--bg-tertiary)';
                  }}
                  onMouseLeave={(e) => {
                    if (currentConvId !== conv.id) e.currentTarget.style.background = 'transparent';
                  }}
                >
                  <span className="truncate flex-1 text-[13px]">{conv.title || '新对话'}</span>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleDelete(conv.id); }}
                    className="opacity-0 group-hover:opacity-100 w-5 h-5 flex items-center justify-center rounded-md transition-all duration-150"
                    style={{ color: 'var(--text-tertiary)' }}
                    onMouseEnter={(e) => e.currentTarget.style.color = '#ef4444'}
                    onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-tertiary)'}
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                      <line x1="2" y1="2" x2="10" y2="10" /><line x1="10" y1="2" x2="2" y2="10" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          </>
        )}

        {/* Bottom: Admin button + User info */}
        <div style={{ borderTop: '1px solid var(--border-light)' }}>
          {isAdmin && (
            <div className="px-3 pt-2">
              <button
                onClick={() => setView(view === 'admin' ? 'chat' : 'admin')}
                className="w-full py-2 px-3 rounded-xl text-sm font-medium flex items-center justify-center gap-1.5 transition-all duration-200"
                style={{
                  background: view === 'admin' ? 'var(--accent-light)' : 'transparent',
                  color: view === 'admin' ? 'var(--accent)' : 'var(--text-secondary)',
                  border: view === 'admin' ? 'none' : '1px solid var(--border-medium)',
                }}
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <path d="M7 1L8.5 4.5L12.5 5L9.5 7.5L10.5 11.5L7 9.5L3.5 11.5L4.5 7.5L1.5 5L5.5 4.5L7 1Z" />
                </svg>
                控制面板
              </button>
            </div>
          )}

          {/* User info */}
          <div className="px-4 py-3 flex items-center gap-2">
            <div
              className="w-7 h-7 rounded-lg flex items-center justify-center text-white text-xs font-bold shrink-0"
              style={{ background: 'var(--accent-gradient)', opacity: 0.8 }}
            >
              {(user?.nickname || user?.username || 'U')[0].toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-xs font-medium truncate" style={{ color: 'var(--text-primary)' }}>
                {user?.nickname || user?.username}
              </div>
              <div className="text-[10px]" style={{ color: 'var(--text-tertiary)' }}>
                {user?.role === 'ADMIN' ? '管理员' : '用户'}
              </div>
            </div>
            <div className="relative">
              <button
                onClick={() => setShowUserMenu(!showUserMenu)}
                className="w-6 h-6 flex items-center justify-center rounded-md transition-colors duration-150"
                style={{ color: 'var(--text-tertiary)' }}
                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <circle cx="7" cy="3.5" r="1.5" /><circle cx="7" cy="7" r="1.5" /><circle cx="7" cy="10.5" r="1.5" />
                </svg>
              </button>
              {showUserMenu && (
                <div
                  className="absolute bottom-full right-0 mb-1 py-1 rounded-xl min-w-[100px]"
                  style={{ background: 'var(--bg-primary)', boxShadow: 'var(--shadow-heavy)', border: '1px solid var(--border-light)' }}
                >
                  <button
                    onClick={handleLogout}
                    className="w-full px-3 py-1.5 text-xs text-left transition-colors duration-150"
                    style={{ color: '#ef4444' }}
                    onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-secondary)'}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                  >
                    退出登录
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        {view === 'admin' ? (
          <AdminPanel onBack={() => setView('chat')} />
        ) : (
          <>
            {/* Top bar */}
            <div
              className="h-12 flex items-center px-3 shrink-0"
              style={{
                background: 'var(--bg-sidebar)',
                backdropFilter: 'blur(12px)',
                WebkitBackdropFilter: 'blur(12px)',
                borderBottom: '1px solid var(--border-light)',
              }}
            >
              <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="w-8 h-8 flex items-center justify-center rounded-lg transition-colors duration-150"
                style={{ color: 'var(--text-secondary)' }}
                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <line x1="4" y1="5" x2="14" y2="5" /><line x1="4" y1="9" x2="14" y2="9" /><line x1="4" y1="13" x2="14" y2="13" />
                </svg>
              </button>
              <span className="ml-2 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                深大智答
              </span>
              {view === 'chat' && (
                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12 }}>
                  <CampusCalendarWidget />
                  <RoleSelector />
                </div>
              )}
            </div>

            {/* Content area */}
            {currentConvId ? (
              <ChatWindow convId={currentConvId} />
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <div className="text-center max-w-md mx-auto px-6 animate-fade-in">
                  <div
                    className="w-16 h-16 rounded-2xl mx-auto mb-5 flex items-center justify-center text-white text-2xl font-bold"
                    style={{ background: 'var(--accent-gradient)' }}
                  >
                    S
                  </div>
                  <h1 className="text-2xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
                    你好，我是深大智答
                  </h1>
                  <p className="mb-8" style={{ color: 'var(--text-secondary)' }}>
                    基于深圳大学知识库的智能问答助手，点击下方开始对话
                  </p>
                  <div className="grid grid-cols-2 gap-2.5">
                    {getDynamicSuggestions(calendar).map((s, i) => (
                      <button
                        key={i}
                        onClick={() => handleSuggestionClick(s.text)}
                        className={`animate-fade-in-up stagger-${i + 1} p-3 rounded-xl text-left text-sm transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]`}
                        style={{
                          background: 'var(--bg-primary)',
                          border: '1px solid var(--border-light)',
                          color: 'var(--text-secondary)',
                          boxShadow: 'var(--shadow-sm)',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.borderColor = 'var(--accent)';
                          e.currentTarget.style.color = 'var(--accent)';
                          e.currentTarget.style.boxShadow = 'var(--shadow-focus)';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.borderColor = 'var(--border-light)';
                          e.currentTarget.style.color = 'var(--text-secondary)';
                          e.currentTarget.style.boxShadow = 'var(--shadow-sm)';
                        }}
                      >
                        <span className="mr-1.5">{s.icon}</span>
                        {s.text}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
