import { useState, type KeyboardEvent } from 'react';

export default function ChatInput({ onSend, disabled }: { onSend: (q: string) => void; disabled: boolean }) {
  const [input, setInput] = useState('');

  const handleSend = () => {
    const q = input.trim();
    if (!q || disabled) return;
    onSend(q);
    setInput('');
  };

  const handleKey = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
  };

  return (
    <div className="p-4 border-t bg-white">
      <div className="flex gap-2 max-w-4xl mx-auto">
        <textarea value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={handleKey}
          placeholder="输入你的问题..." disabled={disabled}
          className="flex-1 resize-none border rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          rows={1} />
        <button onClick={handleSend} disabled={disabled || !input.trim()}
          className="px-4 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed">
          发送
        </button>
      </div>
    </div>
  );
}
