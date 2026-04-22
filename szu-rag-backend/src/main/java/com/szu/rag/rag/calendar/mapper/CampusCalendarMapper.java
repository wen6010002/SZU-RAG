package com.szu.rag.rag.calendar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szu.rag.rag.calendar.model.entity.CampusCalendar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CampusCalendarMapper extends BaseMapper<CampusCalendar> {

    @Select("SELECT * FROM t_campus_calendar WHERE #{date} BETWEEN start_date AND end_date LIMIT 1")
    CampusCalendar findActiveSemester(@Param("date") LocalDate date);

    @Select("SELECT DISTINCT * FROM t_campus_calendar " +
            "WHERE event_start >= #{start} AND event_start <= #{end} " +
            "   OR (event_end IS NOT NULL AND event_end >= #{start} AND event_start <= #{end}) " +
            "ORDER BY event_start")
    List<CampusCalendar> findUpcomingEvents(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("SELECT * FROM t_campus_calendar " +
            "WHERE start_date > #{date} " +
            "ORDER BY start_date ASC LIMIT 1")
    CampusCalendar findNextSemester(@Param("date") LocalDate date);
}
