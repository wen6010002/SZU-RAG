import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Source } from '../api/chat';

const categoryColors: Record<string, string> = {
  enrollment: '#3b82f6', exam: '#ef4444', teaching: '#f59e0b',
  holiday: '#22c55e', admin: '#8b5cf6', logistics: '#06b6d4',
};

const VISIBLE_SOURCES = 3;

function SourceItem({ s, i, isUser }: { s: Source; i: number; isUser: boolean }) {
  return (
    <div
      className="px-3 py-2 rounded-lg text-xs transition-colors duration-150"
      style={{
        background: isUser ? 'rgba(255,255,255,0.12)' : 'var(--accent-light)',
        color: isUser ? 'rgba(255,255,255,0.9)' : 'var(--text-secondary)',
      }}
    >
      <div className="flex items-center gap-2 mb-1">
        <span
          className="shrink-0 w-5 h-5 rounded-md flex items-center justify-center text-[10px] font-bold"
          style={{ background: isUser ? 'rgba(255,255,255,0.2)' : 'var(--accent)', color: '#fff' }}
        >
          {i + 1}
        </span>
        {s.url ? (
          <a
            href={s.url}
            target="_blank"
            rel="noopener noreferrer"
            className="truncate hover:underline font-medium"
            style={{ color: isUser ? 'rgba(255,255,255,0.9)' : 'var(--accent)' }}
          >
            {s.title || s.url}
          </a>
        ) : (
          <span className="truncate font-medium">{s.title}</span>
        )}
        <span style={{ marginLeft: 'auto', color: isUser ? 'rgba(255,255,255,0.5)' : '#94a3b8' }}>
          {s.relevance}
        </span>
      </div>
      {(s.department || s.category || s.publishDate) && (
        <div className="flex flex-wrap gap-1 mt-1">
          {s.department && (
            <span style={{ padding: '1px 6px', borderRadius: 4, fontSize: 10, background: '#3b82f615', color: '#3b82f6', border: '1px solid #3b82f625' }}>
              {s.department}
            </span>
          )}
          {s.category && (
            <span style={{ padding: '1px 6px', borderRadius: 4, fontSize: 10, background: (categoryColors[s.category] || '#6b7280') + '15', color: categoryColors[s.category] || '#6b7280', border: `1px solid ${(categoryColors[s.category] || '#6b7280')}25` }}>
              {s.category}
            </span>
          )}
          {s.publishDate && (
            <span style={{ padding: '1px 6px', borderRadius: 4, fontSize: 10, background: '#64748b15', color: '#64748b' }}>
              {s.publishDate}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

function SourcesList({ sources, isUser }: { sources: Source[]; isUser: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const hasMore = sources.length > VISIBLE_SOURCES;
  const visibleSources = expanded ? sources : sources.slice(0, VISIBLE_SOURCES);

  return (
    <div className="mt-3 pt-3" style={{ borderTop: '1px solid var(--border-light)' }}>
      <p className="text-xs font-medium mb-2" style={{ color: isUser ? 'rgba(255,255,255,0.7)' : 'var(--accent)' }}>
        参考来源
      </p>
      <div className="space-y-2">
        {visibleSources.map((s, i) => (
          <SourceItem key={i} s={s} i={i} isUser={isUser} />
        ))}
      </div>
      {hasMore && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="mt-2 text-xs font-medium w-full text-center py-1.5 rounded-lg transition-colors duration-150"
          style={{ color: 'var(--accent)', background: 'var(--accent-light)' }}
        >
          {expanded ? '收起参考来源' : `查看全部 ${sources.length} 条参考来源`}
        </button>
      )}
    </div>
  );
}

export default function MessageBubble({ role, content, sources, isStreaming }: {
  role: string;
  content: string;
  sources?: Source[];
  isStreaming?: boolean;
}) {
  const isUser = role?.toLowerCase() === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[85%] ${
          isUser
            ? 'rounded-2xl rounded-br-md'
            : 'rounded-2xl rounded-bl-md'
        } px-4 py-3`}
        style={
          isUser
            ? { background: 'var(--accent-gradient)', color: '#ffffff' }
            : {
                background: 'var(--bg-primary)',
                borderLeft: '3px solid var(--accent)',
                boxShadow: 'var(--shadow-md)',
                color: 'var(--text-primary)',
              }
        }
      >
        {content ? (
          <div className={`prose prose-sm max-w-none ${isUser ? 'prose-invert' : ''}`}>
            <ReactMarkdown
              key={isStreaming ? 'streaming' : 'done'}
              remarkPlugins={[remarkGfm]}
            >
              {content}
            </ReactMarkdown>
            {isStreaming && (
              <span
                className="inline-block w-0.5 h-4 ml-0.5 align-middle"
                style={{
                  background: isUser ? 'rgba(255,255,255,0.8)' : 'var(--accent)',
                  animation: 'pulse-dot 1s ease-in-out infinite',
                }}
              />
            )}
          </div>
        ) : isStreaming ? (
          <div className="flex items-center gap-1.5 py-1.5">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="w-2 h-2 rounded-full"
                style={{
                  background: 'var(--accent)',
                  animation: `pulse-dot 1.2s ease-in-out infinite`,
                  animationDelay: `${i * 0.2}s`,
                }}
              />
            ))}
          </div>
        ) : null}

        {sources && sources.length > 0 && !isStreaming && (
          <SourcesList sources={sources} isUser={isUser} />
        )}
      </div>
    </div>
  );
}
