package com.szu.rag.rag.calendar.controller;

import com.szu.rag.framework.result.Result;
import com.szu.rag.rag.calendar.CampusCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CampusCalendarService calendarService;

    @GetMapping("/context")
    public Result<CampusCalendarService.CalendarContextView> getCalendarContext() {
        return Result.success(calendarService.getCalendarView());
    }
}
