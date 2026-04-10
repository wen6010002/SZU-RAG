const BASE = '/api/v1/knowledge';

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

export async function listBases(): Promise<KnowledgeBase[]> {
  const res = await fetch(`${BASE}/bases`);
  const json = await res.json();
  return json.data || [];
}

export async function createBase(name: string, description: string): Promise<KnowledgeBase> {
  const res = await fetch(`${BASE}/bases`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, description }),
  });
  const json = await res.json();
  return json.data;
}

export async function uploadDocument(kbId: string, file: File): Promise<KnowledgeDocument> {
  const form = new FormData();
  form.append('file', file);
  form.append('knowledgeBaseId', kbId);
  const res = await fetch(`${BASE}/documents/upload`, { method: 'POST', body: form });
  const json = await res.json();
  return json.data;
}

export async function uploadDocumentsBatch(kbId: string, files: File[]): Promise<KnowledgeDocument[]> {
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  form.append('knowledgeBaseId', kbId);
  const res = await fetch(`${BASE}/documents/upload/batch`, { method: 'POST', body: form });
  const json = await res.json();
  return json.data || [];
}

export async function listDocuments(kbId: string): Promise<KnowledgeDocument[]> {
  const res = await fetch(`${BASE}/bases/${kbId}/documents`);
  const json = await res.json();
  return json.data || [];
}

export async function getDocument(docId: string): Promise<KnowledgeDocument> {
  const res = await fetch(`${BASE}/documents/${docId}`);
  const json = await res.json();
  return json.data;
}

export async function deleteDocument(docId: string): Promise<void> {
  await fetch(`${BASE}/documents/${docId}`, { method: 'DELETE' });
}

export async function reprocessDocument(docId: string): Promise<KnowledgeDocument> {
  const res = await fetch(`${BASE}/documents/${docId}/reprocess`, { method: 'POST' });
  const json = await res.json();
  return json.data;
}
