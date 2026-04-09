import { useEffect, useState } from 'react';
import { useChatStore } from './store/chatStore';
import * as api from './api/chat';
import ChatWindow from './components/ChatWindow';

export default function App() {
  const { conversations, currentConvId, setConversations, setCurrentConv } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  useEffect(() => { loadConversations(); }, []);

  const loadConversations = async () => {
    const convs = await api.listConversations();
    setConversations(convs);
  };

  const handleNewChat = async () => {
    const conv = await api.createConversation();
    setConversations([conv, ...conversations]);
    setCurrentConv(conv.id);
  };

  const handleDelete = async (id: string) => {
    await api.deleteConversation(id);
    if (currentConvId === id) setCurrentConv(null);
    loadConversations();
  };

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <div className={`${sidebarOpen ? 'w-64' : 'w-0'} transition-all duration-200 bg-white border-r overflow-hidden flex flex-col`}>
        <div className="p-3 border-b">
          <button onClick={handleNewChat} className="w-full py-2 px-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium">
            + 新对话
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {conversations.map((conv) => (
            <div key={conv.id} className={`flex items-center justify-between p-2 rounded-lg cursor-pointer text-sm hover:bg-gray-100 ${currentConvId === conv.id ? 'bg-blue-50 text-blue-700' : 'text-gray-700'}`}
              onClick={() => setCurrentConv(conv.id)}>
              <span className="truncate flex-1">{conv.title || '新对话'}</span>
              <button onClick={(e) => { e.stopPropagation(); handleDelete(conv.id); }} className="text-gray-400 hover:text-red-500 ml-1">x</button>
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1 flex flex-col">
        <div className="p-2 border-b flex items-center">
          <button onClick={() => setSidebarOpen(!sidebarOpen)} className="text-gray-500 hover:text-gray-700 px-2">☰</button>
          <span className="ml-2 text-sm text-gray-500">深大智答 — SZU-RAG</span>
        </div>
        {currentConvId ? <ChatWindow convId={currentConvId} /> : (
          <div className="flex-1 flex items-center justify-center text-gray-400">
            <div className="text-center">
              <p className="text-2xl mb-2">🎓 深大智答</p>
              <p>点击"新对话"开始提问</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
