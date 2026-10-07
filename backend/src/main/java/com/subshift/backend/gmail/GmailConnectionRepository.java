package com.subshift.backend.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Integer> {

    Optional<GmailConnection> findByUserId(Integer userId);
}
