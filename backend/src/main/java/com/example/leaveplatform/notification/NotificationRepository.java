package com.example.leaveplatform.notification;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Every query here carries the recipient. A message is only ever read by the
     * account it was written for, so the username is not a filter somebody may
     * leave blank - it is part of the shape of each statement.
     */
    @Query("""
            select n from Notification n
            where n.recipientUsername = :recipient
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findAllFor(@Param("recipient") String recipient, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.recipientUsername = :recipient and n.readAt is null
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findUnreadFor(@Param("recipient") String recipient, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.recipientUsername = :recipient and n.readAt is null and n.id in :ids
            """)
    List<Notification> findUnreadWithIds(@Param("recipient") String recipient, @Param("ids") Collection<Long> ids);

    /**
     * The same list narrowed to one kind, which is what a screen asks for when it
     * marks its own rows. Two statements rather than one with a nullable kind, for
     * the Oracle reason written up under {@code leave/}.
     */
    @Query("""
            select n from Notification n
            where n.recipientUsername = :recipient and n.readAt is null and n.id in :ids
              and n.kind = :kind
            """)
    List<Notification> findUnreadOfKindWithIds(@Param("recipient") String recipient,
            @Param("ids") Collection<Long> ids, @Param("kind") String kind);

    long countByRecipientUsernameAndReadAtIsNull(String recipientUsername);
}
