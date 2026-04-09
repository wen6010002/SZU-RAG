import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Source } from '../api/chat';

export default function MessageBubble({ role, content, sources, isStreaming }: { role: string; content: string; sources?: Source[]; isStreaming?: boolean }) {
  const isUser = role?.toLowerCase() === 'user';
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[80%] rounded-2xl px-4 py-3 ${isUser ? 'bg-blue-600 text-white' : 'bg-white border shadow-sm text-gray-800'}`}>
        {content ? (
          <div className={`prose prose-sm max-w-none ${isUser ? 'prose-invert' : ''}`}>
            <ReactMarkdown key={isStreaming ? 'streaming' : 'done'} remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
            {isStreaming && <span className="inline-block w-1.5 h-4 bg-current animate-pulse ml-0.5" />}
          </div>
        ) : isStreaming ? (
          <div className="flex space-x-1 py-1">
            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
          </div>
        ) : null}
        {sources && sources.length > 0 && (
          <div className="mt-2 pt-2 border-t border-gray-200">
            <p className="text-xs text-gray-500 mb-1">📎 参考来源：</p>
            {sources.map((s, i) => (
              <div key={i} className="text-xs text-gray-600 flex items-center gap-1 py-0.5">
                <span className="text-blue-500">[{s.relevance}]</span>
                {s.url ? <a href={s.url} target="_blank" className="text-blue-600 hover:underline truncate">{s.title || s.url}</a> : <span>{s.title}</span>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
