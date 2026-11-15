package com.centram.classzy.communication;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/** Plain SQL access to the comm_settings key/value table. */
@Component
public class CommSettingsStore {

    private final JdbcTemplate jdbc;

    public CommSettingsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> load(Long inst, String section) {
        ResultSetExtractor<Map<String, String>> read = (ResultSet rs) -> {
            Map<String, String> out = new LinkedHashMap<>();
            while (rs.next()) out.put(rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2));
            return out;
        };
        return jdbc.query("select setting_key, setting_value from comm_settings where institution_id = ? and section = ?", read, inst, section);
    }

    public void put(Long inst, String section, String key, String value) {
        jdbc.update("insert into comm_settings (institution_id, section, setting_key, setting_value) values (?, ?, ?, ?) "
                + "on conflict (institution_id, section, setting_key) do update set setting_value = excluded.setting_value", inst, section, key, value);
    }

    /** True only for the very first caller per institution; that caller seeds the default lists. */
    /** True only for the first caller per institution and key; that caller seeds defaults. */
    public boolean mark(Long inst, String key) {
        return jdbc.update("insert into comm_settings (institution_id, section, setting_key, setting_value) values (?, '_META', ?, '1') on conflict do nothing", inst, key) == 1;
    }

    boolean markSeeded(Long inst) {
        return jdbc.update("insert into comm_settings (institution_id, section, setting_key, setting_value) values (?, '_META', 'seeded', '1') on conflict do nothing", inst) == 1;
    }
}
