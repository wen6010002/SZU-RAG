import { useEffect, useRef, useState } from 'react';
import { useKnowledgeStore } from '../store/knowledgeStore';
import * as api from '../api/knowledge';
import CreateKBDialog from './CreateKBDialog';

const STATUS_MAP: Record<string, { label: string; bg: string; text: string; animate?: boolean }> = {
  UPLOADED: { label: '已上传', bg: 'var(--bg-tertiary)', text: 'var(--text-secondary)' },
  FETCHING: { label: '读取中', bg: '#fef3c7', text: '#92400e', animate: true },
  PARSING: { label: '解析中', bg: '#fef3c7', text: '#92400e', animate: true },
  CHUNKING: { label: '分块中', bg: '#fef3c7', text: '#92400e', animate: true },
  INDEXING: { label: '入库中', bg: 'var(--accent-light)', text: 'var(--accent)', animate: true },
  COMPLETED: { label: '已完成', bg: 'var(--accent-light)', text: 'var(--accent)' },
  FAILED: { label: '失败', bg: '#fef2f2', text: '#dc2626' },
};

const TERMINAL_STATES = ['COMPLETED', 'FAILED'];
const ACCEPTED_TYPES = '.pdf,.doc,.docx,.md,.txt,.xlsx,.pptx,.ppt';

const FILE_ICONS: Record<string, string> = {
  pdf: '📕', doc: '📘', docx: '📘', md: '📝', txt: '📄',
  xlsx: '📊', pptx: '📑', ppt: '📑',
};

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function getFileIcon(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  return FILE_ICONS[ext] || '📄';
}

function StatusBadge({ status }: { status: string }) {
  const info = STATUS_MAP[status] || { label: status, bg: 'var(--bg-tertiary)', text: 'var(--text-tertiary)' };
  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium"
      style={{ background: info.bg, color: info.text }}
    >
      {info.animate && (
        <span
          className="w-1.5 h-1.5 rounded-full"
          style={{
            background: info.text,
            animation: 'pulse-dot 1.2s ease-in-out infinite',
          }}
        />
      )}
      {info.label}
    </span>
  );
}

export default function KnowledgePanel({ isAdmin = false }: { isAdmin?: boolean }) {
  const {
    knowledgeBases, selectedKbId, documents, loading,
    setKnowledgeBases, setSelectedKbId, setDocuments, updateDocument,
    removeDocument, addDocuments, setLoading,
  } = useKnowledgeStore();

  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadBases();
    return () => stopPolling();
  }, []);

  useEffect(() => {
    if (selectedKbId) {
      loadDocuments(selectedKbId);
    } else {
      setDocuments([]);
    }
  }, [selectedKbId]);

  useEffect(() => {
    const hasActive = documents.some((d) => !TERMINAL_STATES.includes(d.documentStatus));
    if (hasActive) {
      startPolling();
    } else {
      stopPolling();
    }
  }, [documents]);

  const loadBases = async () => {
    try {
      const kbs = await api.listBases();
      setKnowledgeBases(kbs);
    } catch (e) {
      console.error('Failed to load knowledge bases', e);
    }
  };

  const loadDocuments = async (kbId: string) => {
    setLoading(true);
    try {
      const docs = await api.listDocuments(kbId);
      setDocuments(docs);
    } catch (e) {
      console.error('Failed to load documents', e);
    } finally {
      setLoading(false);
    }
  };

  const startPolling = () => {
    if (pollingRef.current) return;
    pollingRef.current = setInterval(async () => {
      const activeDocs = documents.filter((d) => !TERMINAL_STATES.includes(d.documentStatus));
      if (activeDocs.length === 0) {
        stopPolling();
        return;
      }
      for (const doc of activeDocs) {
        try {
          const updated = await api.getDocument(doc.id);
          updateDocument(updated);
        } catch {
          // ignore individual polling errors
        }
      }
    }, 3000);
  };

  const stopPolling = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  };

  const handleUpload = async (files: FileList | File[]) => {
    if (!selectedKbId || files.length === 0) return;
    setUploading(true);
    try {
      const newDocs = await api.uploadDocumentsBatch(selectedKbId, Array.from(files));
      addDocuments(newDocs);
      loadBases();
    } catch (e) {
      alert('上传失败: ' + (e as Error).message);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (docId: string) => {
    if (!confirm('确定删除该文档？此操作不可恢复。')) return;
    try {
      await api.deleteDocument(docId);
      removeDocument(docId);
      loadBases();
    } catch (e) {
      alert('删除失败: ' + (e as Error).message);
    }
  };

  const handleReprocess = async (docId: string) => {
    try {
      const updated = await api.reprocessDocument(docId);
      updateDocument(updated);
    } catch (e) {
      alert('重新处理失败: ' + (e as Error).message);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) {
      handleUpload(e.dataTransfer.files);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = () => setDragOver(false);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleUpload(e.target.files);
      e.target.value = '';
    }
  };

  const selectedKb = knowledgeBases.find((kb) => kb.id === selectedKbId);

  return (
    <div className="flex h-full">
      {/* Left: KB List */}
      <div
        className="w-[240px] shrink-0 flex flex-col"
        style={{
          background: 'var(--bg-sidebar)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          borderRight: '1px solid var(--border-light)',
        }}
      >
        <div className="px-3 py-3 flex items-center justify-between">
          <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            知识库
          </span>
          {isAdmin && (
            <button
              onClick={() => setShowCreateDialog(true)}
              className="text-xs px-2.5 py-1 rounded-lg text-white font-medium transition-opacity duration-150 hover:opacity-90"
              style={{ background: 'var(--accent-gradient)' }}
            >
              + 新建
            </button>
          )}
        </div>
        <div className="flex-1 overflow-y-auto px-2 pb-2 space-y-1">
          {knowledgeBases.map((kb) => (
            <div
              key={kb.id}
              onClick={() => setSelectedKbId(kb.id)}
              className="p-2.5 rounded-xl cursor-pointer text-sm transition-all duration-200"
              style={{
                background: selectedKbId === kb.id ? 'var(--accent-light)' : 'transparent',
                borderLeft: selectedKbId === kb.id ? '3px solid var(--accent)' : '3px solid transparent',
                color: selectedKbId === kb.id ? 'var(--accent)' : 'var(--text-secondary)',
              }}
              onMouseEnter={(e) => {
                if (selectedKbId !== kb.id) {
                  e.currentTarget.style.background = 'var(--bg-tertiary)';
                }
              }}
              onMouseLeave={(e) => {
                if (selectedKbId !== kb.id) {
                  e.currentTarget.style.background = 'transparent';
                }
              }}
            >
              <div className="font-medium truncate text-[13px]">{kb.name}</div>
              <div className="text-[11px] mt-0.5 flex items-center gap-2" style={{ color: 'var(--text-tertiary)' }}>
                <span>{kb.documentCount} 文档</span>
                <span>·</span>
                <span>{kb.chunkCount} 分块</span>
              </div>
            </div>
          ))}
          {knowledgeBases.length === 0 && (
            <div className="text-center py-10" style={{ color: 'var(--text-tertiary)' }}>
              <div className="text-3xl mb-2">📚</div>
              <p className="text-sm">暂无知识库</p>
              <p className="text-xs mt-1">点击"新建"创建</p>
            </div>
          )}
        </div>
      </div>

      {/* Right: Documents */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0" style={{ background: 'var(--bg-secondary)' }}>
        {!selectedKb ? (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center animate-fade-in">
              <div
                className="w-16 h-16 rounded-2xl mx-auto mb-4 flex items-center justify-center text-2xl"
                style={{ background: 'var(--accent-light)' }}
              >
                📚
              </div>
              <p className="text-lg font-medium" style={{ color: 'var(--text-primary)' }}>
                选择或创建一个知识库
              </p>
              <p className="text-sm mt-1" style={{ color: 'var(--text-tertiary)' }}>
                上传文档，构建专属知识库
              </p>
            </div>
          </div>
        ) : (
          <>
            {/* Header */}
            <div
              className="px-6 py-4 shrink-0"
              style={{
                background: 'var(--bg-primary)',
                borderBottom: '1px solid var(--border-light)',
              }}
            >
              <h2 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                {selectedKb.name}
              </h2>
              {selectedKb.description && (
                <p className="text-sm mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                  {selectedKb.description}
                </p>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {/* Upload Zone - admin only */}
              {isAdmin && (
              <div
                onDrop={handleDrop}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onClick={() => fileInputRef.current?.click()}
                className="rounded-2xl p-6 text-center cursor-pointer transition-all duration-200"
                style={{
                  background: dragOver ? 'var(--accent-light)' : 'var(--bg-primary)',
                  border: `2px dashed ${dragOver ? 'var(--accent)' : 'var(--border-medium)'}`,
                  opacity: uploading ? 0.5 : 1,
                  pointerEvents: uploading ? 'none' : 'auto',
                }}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept={ACCEPTED_TYPES}
                  onChange={handleFileSelect}
                  className="hidden"
                />
                <div
                  className="w-12 h-12 rounded-2xl mx-auto mb-3 flex items-center justify-center text-xl"
                  style={{ background: 'var(--accent-light)' }}
                >
                  📄
                </div>
                <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                  {uploading ? '上传中...' : '拖拽文件到此处，或点击选择文件（支持批量上传）'}
                </p>
                <p className="text-xs mt-1.5" style={{ color: 'var(--text-tertiary)' }}>
                  支持 PDF, DOC, DOCX, MD, TXT, XLSX, PPTX
                </p>
              </div>
              )}

              {/* Document List */}
              {loading ? (
                <div className="text-center py-10" style={{ color: 'var(--text-tertiary)' }}>
                  <div className="flex items-center justify-center gap-1.5">
                    {[0, 1, 2].map((i) => (
                      <div
                        key={i}
                        className="w-2 h-2 rounded-full"
                        style={{
                          background: 'var(--accent)',
                          animation: 'pulse-dot 1.2s ease-in-out infinite',
                          animationDelay: `${i * 0.2}s`,
                        }}
                      />
                    ))}
                  </div>
                  <p className="text-sm mt-2">加载中...</p>
                </div>
              ) : documents.length === 0 ? (
                <div className="text-center py-10" style={{ color: 'var(--text-tertiary)' }}>
                  暂无文档，上传文件开始使用
                </div>
              ) : (
                <div className="space-y-2">
                  {documents.map((doc, idx) => (
                    <div
                      key={doc.id}
                      className={`flex items-center justify-between p-3.5 rounded-xl animate-fade-in-up stagger-${Math.min(idx, 5)}`}
                      style={{
                        background: 'var(--bg-primary)',
                        border: '1px solid var(--border-light)',
                        boxShadow: 'var(--shadow-sm)',
                        transition: 'box-shadow 0.2s, border-color 0.2s',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.boxShadow = 'var(--shadow-md)';
                        e.currentTarget.style.borderColor = 'var(--border-medium)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.boxShadow = 'var(--shadow-sm)';
                        e.currentTarget.style.borderColor = 'var(--border-light)';
                      }}
                    >
                      <div className="flex-1 min-w-0 mr-3">
                        <div className="flex items-center gap-2">
                          <span className="text-base">{getFileIcon(doc.fileName || doc.title)}</span>
                          <span className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
                            {doc.fileName || doc.title}
                          </span>
                          <StatusBadge status={doc.documentStatus} />
                        </div>
                        <div className="flex items-center gap-3 mt-1.5 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                          <span>{formatSize(doc.fileSize)}</span>
                          {doc.chunkCount != null && doc.chunkCount > 0 && (
                            <span>{doc.chunkCount} 分块</span>
                          )}
                          <span>{new Date(doc.createdAt).toLocaleString('zh-CN')}</span>
                        </div>
                        {doc.errorMessage && (
                          <p className="text-xs text-red-500 mt-1 truncate">{doc.errorMessage}</p>
                        )}
                      </div>
                      <div className="flex items-center gap-0.5 shrink-0">
                        {isAdmin && (doc.documentStatus === 'COMPLETED' || doc.documentStatus === 'FAILED') && (
                          <button
                            onClick={() => handleReprocess(doc.id)}
                            className="w-8 h-8 flex items-center justify-center rounded-lg transition-all duration-150"
                            style={{ color: 'var(--text-tertiary)' }}
                            onMouseEnter={(e) => {
                              e.currentTarget.style.background = 'var(--accent-light)';
                              e.currentTarget.style.color = 'var(--accent)';
                            }}
                            onMouseLeave={(e) => {
                              e.currentTarget.style.background = 'transparent';
                              e.currentTarget.style.color = 'var(--text-tertiary)';
                            }}
                            title="重新处理"
                          >
                            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                              <path d="M1 1v4.5h4.5" /><path d="M2.5 9.5a5 5 0 1 0 1-5L1 5.5" />
                            </svg>
                          </button>
                        )}
                        {isAdmin && (
                        <button
                          onClick={() => handleDelete(doc.id)}
                          className="w-8 h-8 flex items-center justify-center rounded-lg transition-all duration-150"
                          style={{ color: 'var(--text-tertiary)' }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.background = '#fef2f2';
                            e.currentTarget.style.color = '#ef4444';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.background = 'transparent';
                            e.currentTarget.style.color = 'var(--text-tertiary)';
                          }}
                          title="删除"
                        >
                          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                            <line x1="3" y1="3" x2="11" y2="11" /><line x1="11" y1="3" x2="3" y2="11" />
                          </svg>
                        </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>

      {/* Create KB Dialog */}
      {showCreateDialog && (
        <CreateKBDialog
          onClose={() => setShowCreateDialog(false)}
          onCreated={(kb) => {
            setKnowledgeBases([kb, ...knowledgeBases]);
            setSelectedKbId(kb.id);
          }}
        />
      )}
    </div>
  );
}
