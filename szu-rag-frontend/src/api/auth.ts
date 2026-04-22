const BASE = '/api/v1';

export interface UserVO {
  id: string;
  username: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: number;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function parseResponse<T>(res: Response): Promise<T> {
  const json = await res.json();
  if (json.code !== '200') {
    throw new Error(json.message || '请求失败');
  }
  return json.data;
}

// ========== Auth ==========

export async function login(username: string, password: string): Promise<{ user: UserVO; token: string }> {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json = await res.json();
  if (json.code !== '200') throw new Error(json.message || '登录失败');
  const data = json.data;
  return { user: data.user, token: data.token || '' };
}

export async function register(username: string, password: string, nickname: string): Promise<UserVO> {
  const res = await fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, nickname }),
  });
  return parseResponse<UserVO>(res);
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${BASE}/auth/logout`, {
      method: 'POST',
      headers: { ...authHeaders() },
    });
  } catch { /* ignore */ }
}

export async function getCurrentUser(): Promise<UserVO> {
  const res = await fetch(`${BASE}/auth/current`, {
    headers: { ...authHeaders() },
  });
  return parseResponse<UserVO>(res);
}

// ========== Admin User Management ==========

export async function listUsers(page = 1, size = 20, keyword = ''): Promise<PageResult<UserVO>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (keyword) params.set('keyword', keyword);
  const res = await fetch(`${BASE}/admin/users?${params}`, {
    headers: { ...authHeaders() },
  });
  return parseResponse<PageResult<UserVO>>(res);
}

export async function createUser(data: {
  username: string; password: string; nickname: string; role: string;
}): Promise<UserVO> {
  const res = await fetch(`${BASE}/admin/users`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(data),
  });
  return parseResponse<UserVO>(res);
}

export async function updateUser(id: string, data: {
  nickname?: string; role?: string; status?: number;
}): Promise<UserVO> {
  const res = await fetch(`${BASE}/admin/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(data),
  });
  return parseResponse<UserVO>(res);
}

export async function deleteUser(id: string): Promise<void> {
  const res = await fetch(`${BASE}/admin/users/${id}`, {
    method: 'DELETE',
    headers: { ...authHeaders() },
  });
  if (!res.ok) {
    const json = await res.json().catch(() => ({ message: '删除失败' }));
    throw new Error(json.message || '删除失败');
  }
}

export { authHeaders };
