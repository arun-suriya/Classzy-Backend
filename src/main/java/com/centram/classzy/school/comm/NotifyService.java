package com.centram.classzy.school.comm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the school's in-app notifications (the bell and the Notifications tab). Other services call this when something needs attention. */
@Service
public class NotifyService {

    private final JdbcTemplate jdbc;

    public NotifyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void add(long institutionId, String kind, String title, String body, String link) {
        String b = body == null ? null : (body.length() > 400 ? body.substring(0, 400) : body);
        jdbc.update("insert into school_notifications (institution_id, kind, title, body, link) values (?, ?, ?, ?, ?)", institutionId, kind, title.length() > 150 ? title.substring(0, 150) : title, b, link);
    }
}
