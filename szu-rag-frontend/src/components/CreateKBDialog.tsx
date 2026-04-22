import { useState } from 'react';
import * as api from '../api/knowledge';
import type { KnowledgeBase } from '../api/knowledge';

interface Props {
  onClose: () => void;
  onCreated: (kb: KnowledgeBase) => void;
}

export default function CreateKBDialog({ onClose, onCreated }: Props) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!name.trim()) return;
    setSubmitting(true);
    try {
      const kb = await api.createBase(name.trim(), description.trim());
      onCreated(kb);
      onClose();
    } catch (e) {
      alert('创建失败: ' + (e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 flex items-center justify-center z-50 animate-fade-in"
      style={{ background: 'rgba(0, 0, 0, 0.25)', backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md p-6 animate-scale-in"
        style={{
          background: 'var(--bg-primary)',
          borderRadius: 'var(--radius-xl)',
          boxShadow: 'var(--shadow-lg)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center gap-3 mb-5">
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center text-white text-sm"
            style={{ background: 'var(--accent-gradient)' }}
          >
            📚
          </div>
          <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            新建知识库
          </h3>
        </div>

        {/* Form */}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1.5" style={{ color: 'var(--text-primary)' }}>
              名称 <span style={{ color: 'var(--accent)' }}>*</span>
            </label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="输入知识库名称"
              className="w-full px-3.5 py-2.5 text-sm outline-none transition-all duration-200"
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-medium)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-primary)',
              }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = 'var(--accent)';
                e.currentTarget.style.boxShadow = 'var(--shadow-focus)';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = 'var(--border-medium)';
                e.currentTarget.style.boxShadow = 'none';
              }}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1.5" style={{ color: 'var(--text-primary)' }}>
              描述
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="输入知识库描述（可选）"
              rows={3}
              className="w-full px-3.5 py-2.5 text-sm outline-none resize-none transition-all duration-200"
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-medium)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-primary)',
              }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = 'var(--accent)';
                e.currentTarget.style.boxShadow = 'var(--shadow-focus)';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = 'var(--border-medium)';
                e.currentTarget.style.boxShadow = 'none';
              }}
            />
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-2.5 mt-6">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm rounded-xl transition-colors duration-150"
            style={{ color: 'var(--text-secondary)' }}
            onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
          >
            取消
          </button>
          <button
            onClick={handleSubmit}
            disabled={!name.trim() || submitting}
            className="px-5 py-2 text-white text-sm font-medium rounded-xl transition-all duration-200 hover:opacity-90 active:scale-[0.98]"
            style={{
              background: name.trim() && !submitting ? 'var(--accent-gradient)' : 'var(--bg-tertiary)',
              color: name.trim() && !submitting ? '#ffffff' : 'var(--text-tertiary)',
              cursor: name.trim() && !submitting ? 'pointer' : 'not-allowed',
            }}
          >
            {submitting ? '创建中...' : '创建'}
          </button>
        </div>
      </div>
    </div>
  );
}
