const BASE = '/api/v1';

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export interface Conversation { id: string; title: string; status: string; messageCount: number; createdAt: string; }
export interface Message { id: string; conversationId: string; role: string; content: string; sources?: Source[] | string; isStreaming?: boolean; createdAt: string; }
export interface Source {
  title: string;
  url: string;
  relevance: string;
  snippet: string;
  department?: string;
  documentType?: string;
  publishDate?: string;
  category?: string;
}
export interface CalendarContext {
  date: string;
  academicYear: string;
  semester: string;
  startDate: string;
  endDate: string;
  currentWeek: number;
  upcomingEvents: { eventName: string; eventType: string; eventStart: string; eventEnd: string | null }[];
}

export async function createConversation(): Promise<Conversation> {
  const res = await fetch(`${BASE}/chat/conversations`, { method: 'POST', headers: { ...authHeaders() } });
  const json = await res.json();
  return json.data;
}

export async function listConversations(): Promise<Conversation[]> {
  const res = await fetch(`${BASE}/chat/conversations`, { headers: { ...authHeaders() } });
  const json = await res.json();
  return json.data || [];
}

export async function getMessages(convId: string): Promise<Message[]> {
  const res = await fetch(`${BASE}/chat/conversations/${convId}/messages`, { headers: { ...authHeaders() } });
  const json = await res.json();
  return json.data || [];
}

export async function deleteConversation(convId: string) {
  await fetch(`${BASE}/chat/conversations/${convId}`, { method: 'DELETE', headers: { ...authHeaders() } });
}

export async function sendMessageStream(convId: string, question: string, role: string, callbacks: {
  onThinking?: (content: string) => void;
  onContent?: (content: string) => void;
  onSources?: (sources: Source[]) => void;
  onComplete?: (data: any) => void;
  onError?: (code: string, message: string) => void;
}) {
  const res = await fetch(`${BASE}/chat/conversations/${convId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ question, role }),
  });

  const reader = res.body?.getReader();
  if (!reader) return;

  const decoder = new TextDecoder();
  let buffer = '';
  let currentEvent = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('event:')) currentEvent = line.slice(6).trim();
      else if (line.startsWith('data:')) {
        const data = line.slice(5).trim();
        try {
          const parsed = JSON.parse(data);
          if (currentEvent === 'thinking') callbacks.onThinking?.(parsed.content);
          else if (currentEvent === 'content') callbacks.onContent?.(parsed.content);
          else if (currentEvent === 'sources') callbacks.onSources?.(Array.isArray(parsed) ? parsed : (parsed.sources || []));
          else if (currentEvent === 'complete') { callbacks.onComplete?.(parsed); currentEvent = ''; }
          else if (currentEvent === 'error') { callbacks.onError?.(parsed.code, parsed.message); currentEvent = ''; }
        } catch {}
      }
    }
  }
}

export async function getCalendarContext(): Promise<CalendarContext> {
  try {
    const res = await fetch(`${BASE}/calendar/context`, { headers: { ...authHeaders() } });
    const json = await res.json();
    return json.data;
  } catch {
    return { date: '', academicYear: '', semester: '', startDate: '', endDate: '', currentWeek: 0, upcomingEvents: [] };
  }
}
