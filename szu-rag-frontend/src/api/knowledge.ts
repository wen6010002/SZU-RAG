const BASE = '/api/v1/knowledge';

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  collectionName: string;
  embeddingDim: number;
  chunkStrategy: string;
  chunkSize: number;
  chunkOverlap: number;
  documentCount: number;
  chunkCount: number;
  status: string;
  createdAt: string;
}

export interface KnowledgeDocument {
  id: string;
  knowledgeBaseId: string;
  title: string;
  fileName: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  sourceUrl: string;
  sourceType: string;
  documentStatus: string;
  processMode: string;
  errorMessage: string | null;
  chunkCount: number | null;
  userId: number;
  createdAt: string;
  updatedAt: string;
}

async function parseResponse<T>(res: Response): Promise<T> {
  const json = await res.json();
  if (!res.ok || json.code !== '200') {
    throw new Error(json.message || `请求失败 (${res.status})`);
  }
  return json.data;
}

export async function listBases(): Promise<KnowledgeBase[]> {
  const res = await fetch(`${BASE}/bases`, { headers: { ...authHeaders() } });
  const json = await res.json();
  return json.data || [];
}

export async function createBase(name: string, description: string): Promise<KnowledgeBase> {
  const res = await fetch(`${BASE}/bases`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ name, description }),
  });
  return parseResponse<KnowledgeBase>(res);
}

export async function uploadDocument(kbId: string, file: File): Promise<KnowledgeDocument> {
  const form = new FormData();
  form.append('file', file);
  form.append('knowledgeBaseId', kbId);
  const res = await fetch(`${BASE}/documents/upload`, { method: 'POST', headers: { ...authHeaders() }, body: form });
  return parseResponse<KnowledgeDocument>(res);
}

export async function uploadDocumentsBatch(kbId: string, files: File[]): Promise<KnowledgeDocument[]> {
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  form.append('knowledgeBaseId', kbId);
  const res = await fetch(`${BASE}/documents/upload/batch`, { method: 'POST', headers: { ...authHeaders() }, body: form });
  const json = await res.json();
  if (!res.ok || json.code !== '200') {
    throw new Error(json.message || `上传失败 (${res.status})`);
  }
  return json.data || [];
}

export async function listDocuments(kbId: string): Promise<KnowledgeDocument[]> {
  const res = await fetch(`${BASE}/bases/${kbId}/documents`, { headers: { ...authHeaders() } });
  const json = await res.json();
  return json.data || [];
}

export async function getDocument(docId: string): Promise<KnowledgeDocument> {
  const res = await fetch(`${BASE}/documents/${docId}`, { headers: { ...authHeaders() } });
  return parseResponse<KnowledgeDocument>(res);
}

export async function deleteDocument(docId: string): Promise<void> {
  const res = await fetch(`${BASE}/documents/${docId}`, { method: 'DELETE', headers: { ...authHeaders() } });
  if (!res.ok) {
    const json = await res.json().catch(() => ({ message: '删除失败' }));
    throw new Error(json.message || '删除失败');
  }
}

export async function reprocessDocument(docId: string): Promise<KnowledgeDocument> {
  const res = await fetch(`${BASE}/documents/${docId}/reprocess`, { method: 'POST', headers: { ...authHeaders() } });
  return parseResponse<KnowledgeDocument>(res);
}
