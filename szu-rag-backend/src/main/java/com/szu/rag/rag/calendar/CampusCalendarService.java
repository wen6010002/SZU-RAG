package com.szu.rag.rag.calendar;

import com.szu.rag.rag.calendar.mapper.CampusCalendarMapper;
import com.szu.rag.rag.calendar.model.entity.CampusCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampusCalendarService {

    private final CampusCalendarMapper calendarMapper;

    /**
     * 获取当前校园日历上下文，用于注入 Prompt
     * 返回格式："2025-2026学年第二学期第7周（2026-04-10），本学期起止：2026-02-23 ~ 2026-07-03，近期重要节点：..."
     */
    public String getCurrentContext() {
        LocalDate today = LocalDate.now();

        CampusCalendar semester = calendarMapper.findActiveSemester(today);
        if (semester == null) {
            return "当前不在任何学期内（日期：" + today + "）";
        }

        long weekNum = ChronoUnit.WEEKS.between(semester.getStartDate(), today) + 1;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前是%s学年%s学期第%d周（%s）",
                semester.getAcademicYear(),
                semester.getSemester(),
                weekNum,
                today));
        sb.append(String.format("，本学期起止：%s ~ %s",
                semester.getStartDate(),
                semester.getEndDate()));

        List<CampusCalendar> upcoming = calendarMapper.findUpcomingEvents(
                today, today.plusDays(14));
        if (!upcoming.isEmpty()) {
            sb.append("，近期重要节点：");
            for (CampusCalendar event : upcoming) {
                sb.append(String.format("\n- %s（%s ~ %s）",
                        event.getEventName(),
                        event.getEventStart(),
                        event.getEventEnd() != null ? event.getEventEnd() : event.getEventStart()));
            }
        }

        return sb.toString();
    }

    /**
     * 获取当前学期信息
     */
    public CampusCalendar getCurrentSemester() {
        return calendarMapper.findActiveSemester(LocalDate.now());
    }

    /**
     * 获取下一学期信息
     */
    public CampusCalendar getNextSemester() {
        return calendarMapper.findNextSemester(LocalDate.now());
    }

    /**
     * 获取日历上下文供前端展示
     */
    public CalendarContextView getCalendarView() {
        LocalDate today = LocalDate.now();
        CampusCalendar semester = calendarMapper.findActiveSemester(today);
        CalendarContextView view = new CalendarContextView();
        view.setDate(today);

        if (semester == null) {
            view.setSemester("假期");
            view.setCurrentWeek(0);
            return view;
        }

        view.setAcademicYear(semester.getAcademicYear());
        view.setSemester(semester.getSemester());
        view.setStartDate(semester.getStartDate());
        view.setEndDate(semester.getEndDate());
        view.setCurrentWeek((int) ChronoUnit.WEEKS.between(semester.getStartDate(), today) + 1);

        List<CampusCalendar> upcoming = calendarMapper.findUpcomingEvents(today, today.plusDays(14));
        view.setUpcomingEvents(upcoming.stream().map(e -> {
            CalendarContextView.EventItem item = new CalendarContextView.EventItem();
            item.setEventName(e.getEventName());
            item.setEventType(e.getEventType());
            item.setEventStart(e.getEventStart());
            item.setEventEnd(e.getEventEnd());
            return item;
        }).toList());

        return view;
    }

    @lombok.Data
    public static class CalendarContextView {
        private LocalDate date;
        private String academicYear;
        private String semester;
        private LocalDate startDate;
        private LocalDate endDate;
        private int currentWeek;
        private List<EventItem> upcomingEvents;

        @lombok.Data
        public static class EventItem {
            private String eventName;
            private String eventType;
            private LocalDate eventStart;
            private LocalDate eventEnd;
        }
    }
}
