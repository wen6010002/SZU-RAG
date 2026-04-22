import { useEffect, useRef, useState } from 'react';
import { useChatStore } from '../store/chatStore';
import * as api from '../api/chat';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';

export default function ChatWindow({ convId }: { convId: string }) {
  const { messages, isLoading, userRole, addMessage, appendToLastMessage, setLastMessageSources, finalizeLastMessage, setLoading, clearMessages } = useChatStore();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [, setSources] = useState<any[]>([]);

  useEffect(() => {
    loadMessages();
    return () => { clearMessages(); setSources([]); };
  }, [convId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadMessages = async () => {
    clearMessages();
    const msgs = await api.getMessages(convId);
    for (const m of msgs) {
      let parsedSources = undefined;
      if (m.sources && typeof m.sources === 'string') {
        try { parsedSources = JSON.parse(m.sources); } catch {}
      } else if (Array.isArray(m.sources)) {
        parsedSources = m.sources;
      }
      addMessage({ role: m.role, content: m.content, sources: parsedSources });
    }
  };

  const handleSend = async (question: string) => {
    addMessage({ role: 'user', content: question });
    addMessage({ role: 'assistant', content: '', isStreaming: true });
    setLoading(true);
    setSources([]);

    await api.sendMessageStream(convId, question, userRole, {
      onThinking: () => {},
      onContent: (content) => appendToLastMessage(content),
      onSources: (srcs) => { setSources(srcs); setLastMessageSources(srcs); },
      onComplete: () => { finalizeLastMessage(); setLoading(false); },
      onError: (_code, msg) => { appendToLastMessage(`\n\n❌ Error: ${msg}`); finalizeLastMessage(); setLoading(false); },
    });
  };

  return (
    <div className="flex-1 flex flex-col h-full min-h-0">
      {/* Messages area */}
      <div className="flex-1 overflow-y-auto" style={{ background: 'var(--bg-secondary)' }}>
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-5">
          {messages.map((msg, i) => (
            <div key={i} className="animate-fade-in-up">
              <MessageBubble
                role={msg.role}
                content={msg.content}
                sources={msg.sources}
                isStreaming={msg.isStreaming}
              />
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input area */}
      <ChatInput onSend={handleSend} disabled={isLoading} />
    </div>
  );
}
