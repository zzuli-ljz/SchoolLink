package com.schoollink.dbtest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/db")
public class DbTestController {
    private final EntityManager entityManager;
    private final SyncTestRepository repository;

    public DbTestController(EntityManager entityManager, SyncTestRepository repository) {
        this.entityManager = entityManager;
        this.repository = repository;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Query q = entityManager.createNativeQuery("select now(), current_user, current_database()");
        Object[] row = (Object[]) q.getSingleResult();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("now", row[0]);
        res.put("current_user", row[1]);
        res.put("database", row[2]);
        return res;
    }

    @PostMapping("/test-sync")
    public SyncTest create(@RequestBody Map<String, String> body) {
        SyncTest s = new SyncTest();
        s.setDevice(body.getOrDefault("device", "unknown"));
        s.setNote(body.getOrDefault("note", "created via API"));
        return repository.save(s);
    }

    @GetMapping("/test-sync")
    public List<SyncTest> recent() {
        return repository.findTop10ByOrderByIdDesc();
    }
}