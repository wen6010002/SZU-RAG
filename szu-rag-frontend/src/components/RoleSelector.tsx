import { useChatStore } from '../store/chatStore';

const roles = [
  { key: 'student' as const, label: '在校学生', hint: '办事流程 · 截止提醒' },
  { key: 'teacher' as const, label: '教职工', hint: '政策依据 · 审批流程' },
  { key: 'visitor' as const, label: '访客/考生', hint: '招生信息 · 校园导览' },
];

export default function RoleSelector() {
  const { userRole, setUserRole } = useChatStore();

  return (
    <div style={{
      display: 'flex', gap: 2, background: '#f1f5f9',
      borderRadius: 8, padding: 2,
    }}>
      {roles.map(r => (
        <button key={r.key} onClick={() => setUserRole(r.key)} style={{
          padding: '4px 12px', borderRadius: 6, border: 'none',
          cursor: 'pointer', fontSize: 12, fontWeight: 500,
          background: userRole === r.key ? '#fff' : 'transparent',
          color: userRole === r.key ? '#1e40af' : '#64748b',
          boxShadow: userRole === r.key ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
          transition: 'all 0.2s',
        }}>
          {r.label}
        </button>
      ))}
    </div>
  );
}
