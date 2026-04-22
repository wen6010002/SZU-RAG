import { useEffect, useState } from 'react';
import * as authApi from '../api/auth';
import type { UserVO } from '../api/auth';

export default function UserManagement() {
  const [users, setUsers] = useState<UserVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  // 创建/编辑对话框
  const [showModal, setShowModal] = useState(false);
  const [editUser, setEditUser] = useState<UserVO | null>(null);
  const [formUsername, setFormUsername] = useState('');
  const [formPassword, setFormPassword] = useState('');
  const [formNickname, setFormNickname] = useState('');
  const [formRole, setFormRole] = useState('USER');
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const result = await authApi.listUsers(page, 20, keyword);
      setUsers(result.records);
      setTotal(result.total);
    } catch (e: any) {
      console.error('Failed to load users:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadUsers(); }, [page]);

  const handleSearch = () => { setPage(1); loadUsers(); };

  const openCreateModal = () => {
    setEditUser(null);
    setFormUsername('');
    setFormPassword('');
    setFormNickname('');
    setFormRole('USER');
    setFormError('');
    setShowModal(true);
  };

  const openEditModal = (user: UserVO) => {
    setEditUser(user);
    setFormUsername(user.username);
    setFormPassword('');
    setFormNickname(user.nickname);
    setFormRole(user.role);
    setFormError('');
    setShowModal(true);
  };

  const handleSubmit = async () => {
    setFormLoading(true);
    setFormError('');
    try {
      if (editUser) {
        await authApi.updateUser(String(editUser.id), {
          nickname: formNickname,
          role: formRole,
        });
      } else {
        if (!formPassword.trim()) { setFormError('请输入密码'); return; }
        await authApi.createUser({
          username: formUsername,
          password: formPassword,
          nickname: formNickname,
          role: formRole,
        });
      }
      setShowModal(false);
      loadUsers();
    } catch (e: any) {
      setFormError(e.message || '操作失败');
    } finally {
      setFormLoading(false);
    }
  };

  const handleToggleStatus = async (user: UserVO) => {
    try {
      await authApi.updateUser(String(user.id), { status: user.status === 1 ? 0 : 1 });
      loadUsers();
    } catch (e: any) {
      console.error('Failed to toggle status:', e);
    }
  };

  const handleDelete = async (user: UserVO) => {
    if (!confirm(`确定删除用户 "${user.username}" 吗？`)) return;
    try {
      await authApi.deleteUser(String(user.id));
      loadUsers();
    } catch (e: any) {
      alert(e.message || '删除失败');
    }
  };

  const totalPages = Math.ceil(total / 20);

  return (
    <div className="p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
          用户管理
          <span className="ml-2 text-sm font-normal" style={{ color: 'var(--text-tertiary)' }}>
            共 {total} 个用户
          </span>
        </h2>
        <div className="flex items-center gap-2">
          <div className="flex items-center rounded-xl overflow-hidden" style={{ border: '1px solid var(--border-medium)' }}>
            <input
              type="text"
              placeholder="搜索用户名/昵称"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="px-3 py-2 text-sm outline-none w-48"
              style={{ background: 'var(--bg-primary)', color: 'var(--text-primary)' }}
            />
            <button
              onClick={handleSearch}
              className="px-3 py-2 text-sm"
              style={{ color: 'var(--accent)', background: 'var(--bg-secondary)' }}
            >
              搜索
            </button>
          </div>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 rounded-xl text-white text-sm font-medium transition-all duration-200 hover:opacity-90"
            style={{ background: 'var(--accent-gradient)' }}
          >
            + 添加用户
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--border-light)' }}>
        <table className="w-full text-sm">
          <thead>
            <tr style={{ background: 'var(--bg-secondary)' }}>
              <th className="text-left px-4 py-3 font-medium" style={{ color: 'var(--text-secondary)' }}>用户名</th>
              <th className="text-left px-4 py-3 font-medium" style={{ color: 'var(--text-secondary)' }}>昵称</th>
              <th className="text-left px-4 py-3 font-medium" style={{ color: 'var(--text-secondary)' }}>角色</th>
              <th className="text-left px-4 py-3 font-medium" style={{ color: 'var(--text-secondary)' }}>状态</th>
              <th className="text-right px-4 py-3 font-medium" style={{ color: 'var(--text-secondary)' }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="text-center py-8" style={{ color: 'var(--text-tertiary)' }}>加载中...</td></tr>
            ) : users.length === 0 ? (
              <tr><td colSpan={5} className="text-center py-8" style={{ color: 'var(--text-tertiary)' }}>暂无用户</td></tr>
            ) : users.map((user) => (
              <tr key={String(user.id)} className="transition-colors duration-150" style={{ borderTop: '1px solid var(--border-light)' }}
                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-secondary)'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <td className="px-4 py-3" style={{ color: 'var(--text-primary)' }}>{user.username}</td>
                <td className="px-4 py-3" style={{ color: 'var(--text-secondary)' }}>{user.nickname}</td>
                <td className="px-4 py-3">
                  <span className="inline-block px-2 py-0.5 rounded-md text-xs font-medium"
                    style={{
                      background: user.role === 'ADMIN' ? 'var(--accent-light)' : 'var(--bg-tertiary)',
                      color: user.role === 'ADMIN' ? 'var(--accent)' : 'var(--text-secondary)',
                    }}>
                    {user.role === 'ADMIN' ? '管理员' : '用户'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => handleToggleStatus(user)}
                    className="inline-block px-2 py-0.5 rounded-md text-xs font-medium cursor-pointer transition-all duration-150"
                    style={{
                      background: user.status === 1 ? '#dcfce7' : '#fee2e2',
                      color: user.status === 1 ? '#16a34a' : '#dc2626',
                    }}
                  >
                    {user.status === 1 ? '启用' : '禁用'}
                  </button>
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => openEditModal(user)}
                    className="px-2 py-1 rounded-md text-xs transition-colors duration-150"
                    style={{ color: 'var(--accent)' }}
                    onMouseEnter={(e) => e.currentTarget.style.background = 'var(--accent-light)'}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                  >
                    编辑
                  </button>
                  <button
                    onClick={() => handleDelete(user)}
                    className="px-2 py-1 rounded-md text-xs ml-1 transition-colors duration-150"
                    style={{ color: '#ef4444' }}
                    onMouseEnter={(e) => e.currentTarget.style.background = '#fee2e2'}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <button
            onClick={() => setPage(Math.max(1, page - 1))}
            disabled={page === 1}
            className="px-3 py-1 rounded-lg text-sm disabled:opacity-30"
            style={{ background: 'var(--bg-tertiary)', color: 'var(--text-secondary)' }}
          >
            上一页
          </button>
          <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
            {page} / {totalPages}
          </span>
          <button
            onClick={() => setPage(Math.min(totalPages, page + 1))}
            disabled={page === totalPages}
            className="px-3 py-1 rounded-lg text-sm disabled:opacity-30"
            style={{ background: 'var(--bg-tertiary)', color: 'var(--text-secondary)' }}
          >
            下一页
          </button>
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 flex items-center justify-center z-50" style={{ background: 'rgba(0,0,0,0.3)' }}
          onClick={(e) => { if (e.target === e.currentTarget) setShowModal(false); }}>
          <div className="w-full max-w-md mx-4 p-6 rounded-2xl" style={{ background: 'var(--bg-primary)', boxShadow: 'var(--shadow-heavy)' }}>
            <h3 className="text-base font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
              {editUser ? '编辑用户' : '添加用户'}
            </h3>
            <div className="space-y-3">
              {!editUser && (
                <input type="text" placeholder="用户名" value={formUsername}
                  onChange={(e) => setFormUsername(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
                  style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)', color: 'var(--text-primary)' }}
                  onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
                  onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'} />
              )}
              {!editUser && (
                <input type="password" placeholder="密码" value={formPassword}
                  onChange={(e) => setFormPassword(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
                  style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)', color: 'var(--text-primary)' }}
                  onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
                  onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'} />
              )}
              <input type="text" placeholder="昵称" value={formNickname}
                onChange={(e) => setFormNickname(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
                style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)', color: 'var(--text-primary)' }}
                onFocus={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
                onBlur={(e) => e.currentTarget.style.borderColor = 'var(--border-medium)'} />
              <select value={formRole} onChange={(e) => setFormRole(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
                style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)', color: 'var(--text-primary)' }}>
                <option value="USER">普通用户</option>
                <option value="ADMIN">管理员</option>
              </select>
              {formError && <p className="text-xs" style={{ color: '#ef4444' }}>{formError}</p>}
            </div>
            <div className="flex justify-end gap-2 mt-5">
              <button onClick={() => setShowModal(false)}
                className="px-4 py-2 rounded-xl text-sm"
                style={{ color: 'var(--text-secondary)', background: 'var(--bg-tertiary)' }}>
                取消
              </button>
              <button onClick={handleSubmit} disabled={formLoading}
                className="px-4 py-2 rounded-xl text-white text-sm font-medium disabled:opacity-50"
                style={{ background: 'var(--accent-gradient)' }}>
                {formLoading ? '提交中...' : '确定'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
