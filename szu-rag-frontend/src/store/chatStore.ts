import { create } from 'zustand';
import type { Conversation, Source } from '../api/chat';

interface DisplayMessage {
  role: string;
  content: string;
  sources?: Source[];
  isStreaming?: boolean;
}

interface ChatState {
  conversations: Conversation[];
  currentConvId: string | null;
  messages: DisplayMessage[];
  isLoading: boolean;
  userRole: 'student' | 'teacher' | 'visitor';
  setConversations: (convs: Conversation[]) => void;
  setCurrentConv: (id: string | null) => void;
  addMessage: (msg: any) => void;
  appendToLastMessage: (content: string) => void;
  setLastMessageSources: (sources: Source[]) => void;
  finalizeLastMessage: () => void;
  clearMessages: () => void;
  setLoading: (loading: boolean) => void;
  setUserRole: (role: 'student' | 'teacher' | 'visitor') => void;
}

export const useChatStore = create<ChatState>((set) => ({
  conversations: [],
  currentConvId: null,
  messages: [],
  isLoading: false,
  userRole: 'student',
  setConversations: (convs) => set({ conversations: convs }),
  setCurrentConv: (id) => set({ currentConvId: id, messages: [] }),
  addMessage: (msg) => set((state) => ({ messages: [...state.messages, msg] })),
  appendToLastMessage: (content) => set((state) => {
    const msgs = [...state.messages];
    const last = msgs[msgs.length - 1];
    if (last && last.role === 'assistant') {
      msgs[msgs.length - 1] = { ...last, content: (last.content || '') + content, isStreaming: true };
    }
    return { messages: msgs };
  }),
  setLastMessageSources: (sources) => set((state) => {
    const msgs = [...state.messages];
    const last = msgs[msgs.length - 1];
    if (last) msgs[msgs.length - 1] = { ...last, sources };
    return { messages: msgs };
  }),
  finalizeLastMessage: () => set((state) => {
    const msgs = [...state.messages];
    const last = msgs[msgs.length - 1];
    if (last) msgs[msgs.length - 1] = { ...last, isStreaming: false };
    return { messages: msgs };
  }),
  clearMessages: () => set({ messages: [] }),
  setLoading: (loading) => set({ isLoading: loading }),
  setUserRole: (role) => set({ userRole: role }),
}));
