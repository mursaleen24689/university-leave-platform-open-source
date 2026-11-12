package com.example.leaveplatform.mail;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailAttemptRepository extends JpaRepository<MailAttempt, Long> {

    List<MailAttempt> findAllByOrderByIdDesc(Pageable page);
}
