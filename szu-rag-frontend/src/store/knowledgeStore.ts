import { create } from 'zustand';
import type { KnowledgeBase, KnowledgeDocument } from '../api/knowledge';

interface KnowledgeState {
  knowledgeBases: KnowledgeBase[];
  selectedKbId: string | null;
  documents: KnowledgeDocument[];
  loading: boolean;
  setKnowledgeBases: (kbs: KnowledgeBase[]) => void;
  setSelectedKbId: (id: string | null) => void;
  setDocuments: (docs: KnowledgeDocument[]) => void;
  updateDocument: (doc: KnowledgeDocument) => void;
  removeDocument: (docId: string) => void;
  addDocuments: (docs: KnowledgeDocument[]) => void;
  setLoading: (loading: boolean) => void;
}

export const useKnowledgeStore = create<KnowledgeState>((set) => ({
  knowledgeBases: [],
  selectedKbId: null,
  documents: [],
  loading: false,

  setKnowledgeBases: (kbs) => set({ knowledgeBases: kbs }),
  setSelectedKbId: (id) => set({ selectedKbId: id }),
  setDocuments: (docs) => set({ documents: docs }),
  setLoading: (loading) => set({ loading }),

  updateDocument: (doc) => set((state) => ({
    documents: state.documents.map((d) => (d.id === doc.id ? doc : d)),
  })),

  removeDocument: (docId) => set((state) => ({
    documents: state.documents.filter((d) => d.id !== docId),
  })),

  addDocuments: (docs) => set((state) => ({
    documents: [...docs, ...state.documents],
  })),
}));
