package com.schoollink.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByScope(NoticeScope scope);

    List<Notice> findByClassId(Long classId);

    void deleteByClassId(Long classId);

    @Query("SELECT n FROM Notice n WHERE n.scope IN :scopes OR n.classId IN :classIds ORDER BY n.createdAt DESC")
    List<Notice> findForFeed(@Param("scopes") List<NoticeScope> scopes, @Param("classIds") List<Long> classIds);

    @Query("SELECT n FROM Notice n WHERE n.scope IN :scopes ORDER BY n.createdAt DESC")
    List<Notice> findByScopeIn(@Param("scopes") List<NoticeScope> scopes);
}