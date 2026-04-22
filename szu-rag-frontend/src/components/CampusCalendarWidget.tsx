import { useState, useEffect } from 'react';
import { getCalendarContext, type CalendarContext } from '../api/chat';

const eventTypeLabels: Record<string, string> = {
  exam: '考试', enrollment: '选课', holiday: '假期',
  teaching: '教学', registration: '注册',
};

const eventTypeColors: Record<string, string> = {
  exam: '#ef4444', enrollment: '#3b82f6', holiday: '#22c55e',
  teaching: '#f59e0b', registration: '#8b5cf6',
};

export default function CampusCalendarWidget() {
  const [calendar, setCalendar] = useState<CalendarContext | null>(null);

  useEffect(() => {
    getCalendarContext().then(setCalendar);
  }, []);

  if (!calendar || !calendar.semester) return null;

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '6px 16px', fontSize: 13, color: '#64748b',
    }}>
      <span style={{
        background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
        color: '#fff', padding: '2px 10px', borderRadius: 12,
        fontWeight: 600, fontSize: 12,
      }}>
        第{calendar.currentWeek}周
      </span>
      <span>{calendar.semester}</span>
      {calendar.upcomingEvents?.slice(0, 2).map((evt, i) => (
        <span key={i} style={{
          padding: '2px 8px', borderRadius: 8, fontSize: 12,
          background: (eventTypeColors[evt.eventType] || '#6b7280') + '18',
          color: eventTypeColors[evt.eventType] || '#6b7280',
          border: `1px solid ${(eventTypeColors[evt.eventType] || '#6b7280')}33`,
        }}>
          {eventTypeLabels[evt.eventType] || ''} {evt.eventName}
        </span>
      ))}
    </div>
  );
}
