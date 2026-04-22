import { useState } from 'react';
import * as authApi from '../api/auth';
import { useAuthStore } from '../store/authStore';

export default function LoginPage() {
  const { setAuth } = useAuthStore();
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleLogin = async () => {
    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const { user, token } = await authApi.login(username, password);
      setAuth(user, token);
    } catch (e: any) {
      setError(e.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async () => {
    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }
    if (password !== confirmPassword) {
      setError('两次密码输入不一致');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const user = await authApi.register(username, password, nickname || username);
      // 注册后自动登录
      const { token } = await authApi.login(username, password);
      setAuth(user, token);
    } catch (e: any) {
      setError(e.message || '注册失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isRegister) handleRegister();
    else handleLogin();
  };

  return (
    <div className="flex items-center justify-center min-h-screen" style={{ background: 'var(--bg-secondary)' }}>
      <div
        className="w-full max-w-sm mx-4 p-8 rounded-2xl"
        style={{
          background: 'var(--bg-primary)',
          boxShadow: 'var(--shadow-heavy)',
          border: '1px solid var(--border-light)',
        }}
      >
        {/* Logo */}
        <div className="text-center mb-8">
          <div
            className="w-14 h-14 rounded-2xl mx-auto mb-4 flex items-center justify-center text-white text-xl font-bold"
            style={{ background: 'var(--accent-gradient)' }}
          >
            S
          </div>
          <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>深大智答</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-tertiary)' }}>SZU-RAG 智能问答平台</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <input
              type="text"
              placeholder="用户名"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all duration-200"
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-medium)',
                color: 'var(--text-primary)',
              }}
              onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
              onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'}
            />
          </div>

          <div>
            <input
              type="password"
              placeholder="密码"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all duration-200"
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-medium)',
                color: 'var(--text-primary)',
              }}
              onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
              onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'}
            />
          </div>

          {isRegister && (
            <>
              <div>
                <input
                  type="password"
                  placeholder="确认密码"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all duration-200"
                  style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-medium)',
                    color: 'var(--text-primary)',
                  }}
                  onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
                  onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'}
                />
              </div>
              <div>
                <input
                  type="text"
                  placeholder="昵称（选填）"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all duration-200"
                  style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-medium)',
                    color: 'var(--text-primary)',
                  }}
                  onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
                  onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'}
                />
              </div>
            </>
          )}

          {error && (
            <p className="text-xs text-center" style={{ color: '#ef4444' }}>{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-xl text-white text-sm font-medium transition-all duration-200 hover:opacity-90 active:scale-[0.98] disabled:opacity-50"
            style={{ background: 'var(--accent-gradient)' }}
          >
            {loading ? '请稍候...' : (isRegister ? '注册' : '登录')}
          </button>
        </form>

        {/* Switch */}
        <p className="text-center text-sm mt-6" style={{ color: 'var(--text-tertiary)' }}>
          {isRegister ? '已有账号？' : '没有账号？'}
          <button
            onClick={() => { setIsRegister(!isRegister); setError(''); }}
            className="ml-1 font-medium transition-colors duration-150"
            style={{ color: 'var(--accent)' }}
          >
            {isRegister ? '去登录' : '注册'}
          </button>
        </p>
      </div>
    </div>
  );
}
