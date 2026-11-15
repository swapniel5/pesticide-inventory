package com.agroshop.demodata;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DemoDataRegistry {

    private static final String TABLE_NAME = "agroshop_demo_records";

    private final JdbcTemplate jdbcTemplate;

    DemoDataRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureTable() {
        jdbcTemplate.execute("""
                create table if not exists agroshop_demo_records (
                    entity_type varchar(24) not null,
                    entity_id bigint not null,
                    primary key (entity_type, entity_id)
                )
                """);
    }

    List<Long> findIds(DemoDataRecordType type) {
        return jdbcTemplate.queryForList(
                "select entity_id from " + TABLE_NAME + " where entity_type = ? order by entity_id",
                Long.class,
                type.name());
    }

    void record(DemoDataRecordType type, Long id) {
        jdbcTemplate.update(
                "insert into " + TABLE_NAME + " (entity_type, entity_id) values (?, ?)",
                type.name(),
                id);
    }

    void clear() {
        jdbcTemplate.update("delete from " + TABLE_NAME);
    }
}
