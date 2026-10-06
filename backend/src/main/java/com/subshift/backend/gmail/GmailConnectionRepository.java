package com.subshift.backend.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Integer> {
}
