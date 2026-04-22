import { useState, useRef, useEffect, type KeyboardEvent } from 'react';

export default function ChatInput({ onSend, disabled }: { onSend: (q: string) => void; disabled: boolean }) {
  const [input, setInput] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    const q = input.trim();
    if (!q || disabled) return;
    onSend(q);
    setInput('');
  };

  const handleKey = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // Auto-resize textarea
  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 160) + 'px';
    }
  }, [input]);

  return (
    <div
      className="shrink-0 px-4 pb-4 pt-2"
      style={{ background: 'var(--bg-secondary)' }}
    >
      <div
        className="max-w-3xl mx-auto flex items-end gap-2 p-2 rounded-2xl transition-shadow duration-200"
        style={{
          background: 'var(--bg-primary)',
          boxShadow: 'var(--shadow-md)',
          border: '1px solid var(--border-light)',
        }}
      >
        <textarea
          ref={textareaRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder="输入你的问题..."
          disabled={disabled}
          rows={1}
          className="flex-1 resize-none px-3 py-2 text-sm outline-none"
          style={{
            background: 'transparent',
            color: 'var(--text-primary)',
            maxHeight: '160px',
            lineHeight: '1.5',
          }}
        />
        <button
          onClick={handleSend}
          disabled={disabled || !input.trim()}
          className="w-9 h-9 shrink-0 flex items-center justify-center rounded-xl transition-all duration-200"
          style={{
            background: input.trim() && !disabled ? 'var(--accent-gradient)' : 'var(--bg-tertiary)',
            color: input.trim() && !disabled ? '#ffffff' : 'var(--text-tertiary)',
            cursor: input.trim() && !disabled ? 'pointer' : 'not-allowed',
          }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <line x1="8" y1="12" x2="8" y2="3" />
            <polyline points="4,7 8,3 12,7" />
          </svg>
        </button>
      </div>
      <p
        className="text-center text-[11px] mt-2"
        style={{ color: 'var(--text-tertiary)' }}
      >
        按 Enter 发送，Shift+Enter 换行
      </p>
    </div>
  );
}
