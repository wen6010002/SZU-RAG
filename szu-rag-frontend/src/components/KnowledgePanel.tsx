import { useEffect, useRef, useState } from 'react';
import { useKnowledgeStore } from '../store/knowledgeStore';
import * as api from '../api/knowledge';
import CreateKBDialog from './CreateKBDialog';

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  UPLOADED: { label: '已上传', color: 'bg-gray-100 text-gray-600' },
  FETCHING: { label: '读取中', color: 'bg-yellow-100 text-yellow-700' },
  PARSING: { label: '解析中', color: 'bg-yellow-100 text-yellow-700' },
  CHUNKING: { label: '分块中', color: 'bg-yellow-100 text-yellow-700' },
  INDEXING: { label: '入库中', color: 'bg-blue-100 text-blue-700' },
  COMPLETED: { label: '已完成', color: 'bg-green-100 text-green-700' },
  FAILED: { label: '失败', color: 'bg-red-100 text-red-700' },
};

const TERMINAL_STATES = ['COMPLETED', 'FAILED'];
const ACCEPTED_TYPES = '.pdf,.doc,.docx,.md,.txt,.xlsx,.pptx,.ppt';

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function StatusBadge({ status }: { status: string }) {
  const info = STATUS_MAP[status] || { label: status, color: 'bg-gray-100 text-gray-600' };
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.color}`}>
      {info.label}
    </span>
  );
}

export default function KnowledgePanel() {
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

  // Watch for non-terminal documents and start polling
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
      // Reload KB list to update counts
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
      <div className="w-64 border-r bg-gray-50 flex flex-col">
        <div className="p-3 border-b flex items-center justify-between">
          <span className="text-sm font-semibold text-gray-700">知识库</span>
          <button
            onClick={() => setShowCreateDialog(true)}
            className="text-sm px-2 py-1 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            + 新建
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {knowledgeBases.map((kb) => (
            <div
              key={kb.id}
              onClick={() => setSelectedKbId(kb.id)}
              className={`p-2.5 rounded-lg cursor-pointer text-sm transition-colors ${
                selectedKbId === kb.id
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-100 text-gray-700'
              }`}
            >
              <div className="font-medium truncate">{kb.name}</div>
              <div className="text-xs text-gray-500 mt-0.5">
                {kb.documentCount} 文档 · {kb.chunkCount} 分块
              </div>
            </div>
          ))}
          {knowledgeBases.length === 0 && (
            <div className="text-center text-gray-400 text-sm py-8">
              暂无知识库<br />点击"新建"创建
            </div>
          )}
        </div>
      </div>

      {/* Right: Documents */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {!selectedKb ? (
          <div className="flex-1 flex items-center justify-center text-gray-400">
            <div className="text-center">
              <p className="text-4xl mb-3">📚</p>
              <p className="text-lg">选择或创建一个知识库</p>
            </div>
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="p-4 border-b bg-white">
              <h2 className="text-lg font-semibold">{selectedKb.name}</h2>
              {selectedKb.description && (
                <p className="text-sm text-gray-500 mt-0.5">{selectedKb.description}</p>
              )}
            </div>

            {/* Upload Zone */}
            <div
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onClick={() => fileInputRef.current?.click()}
              className={`mx-4 mt-4 border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors ${
                dragOver
                  ? 'border-blue-400 bg-blue-50'
                  : 'border-gray-300 hover:border-gray-400 hover:bg-gray-50'
              } ${uploading ? 'opacity-50 pointer-events-none' : ''}`}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={ACCEPTED_TYPES}
                onChange={handleFileSelect}
                className="hidden"
              />
              <div className="text-3xl mb-2">📄</div>
              <p className="text-sm text-gray-600">
                {uploading ? '上传中...' : '拖拽文件到此处，或点击选择文件（支持批量上传）'}
              </p>
              <p className="text-xs text-gray-400 mt-1">
                支持 PDF, DOC, DOCX, MD, TXT, XLSX, PPTX
              </p>
            </div>

            {/* Document List */}
            <div className="flex-1 overflow-y-auto p-4">
              {loading ? (
                <div className="text-center text-gray-400 py-8">加载中...</div>
              ) : documents.length === 0 ? (
                <div className="text-center text-gray-400 py-8">
                  暂无文档，上传文件开始使用
                </div>
              ) : (
                <div className="space-y-2">
                  {documents.map((doc) => (
                    <div
                      key={doc.id}
                      className="flex items-center justify-between p-3 bg-white rounded-lg border hover:shadow-sm transition-shadow"
                    >
                      <div className="flex-1 min-w-0 mr-3">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium truncate">{doc.fileName || doc.title}</span>
                          <StatusBadge status={doc.documentStatus} />
                        </div>
                        <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
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
                      <div className="flex items-center gap-1 shrink-0">
                        {(doc.documentStatus === 'COMPLETED' || doc.documentStatus === 'FAILED') && (
                          <button
                            onClick={() => handleReprocess(doc.id)}
                            className="p-1.5 text-gray-400 hover:text-blue-600 rounded hover:bg-blue-50"
                            title="重新处理"
                          >
                            ↻
                          </button>
                        )}
                        <button
                          onClick={() => handleDelete(doc.id)}
                          className="p-1.5 text-gray-400 hover:text-red-600 rounded hover:bg-red-50"
                          title="删除"
                        >
                          ✕
                        </button>
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
