import { create } from 'zustand';
import type { UserVO } from '../api/auth';

interface AuthState {
  user: UserVO | null;
  token: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  initialized: boolean;
  setAuth: (user: UserVO, token: string) => void;
  clearAuth: () => void;
  setInitialized: (v: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: localStorage.getItem('token'),
  isAuthenticated: false,
  isAdmin: false,
  initialized: false,
  setAuth: (user, token) => {
    if (token) localStorage.setItem('token', token);
    set({ user, token, isAuthenticated: true, isAdmin: user.role === 'ADMIN' });
  },
  clearAuth: () => {
    localStorage.removeItem('token');
    set({ user: null, token: null, isAuthenticated: false, isAdmin: false });
  },
  setInitialized: (v) => set({ initialized: v }),
}));
