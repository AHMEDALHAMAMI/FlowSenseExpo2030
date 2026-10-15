package com.tuwaiq.flowsense.Repository;

import com.tuwaiq.flowsense.Model.QueueTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QueueTicketRepository extends JpaRepository<QueueTicket, Integer> {

    QueueTicket findQueueTicketById(Integer id);

    QueueTicket findFirstByTicketHashAndStatusIn(String ticketHash, List<String> statuses);

    QueueTicket findFirstByTicketHashOrderByIdDesc(String ticketHash);

    QueueTicket findFirstByTicketHashAndPavilionIdAndStatusIn(String ticketHash, Integer pavilionId, List<String> statuses);

    List<QueueTicket> findAllByPavilionIdAndStatusOrderByJoinedAtAsc(Integer pavilionId, String status);

    List<QueueTicket> findAllByStatusIn(List<String> statuses);

    List<QueueTicket> findAllByStatusAndCalledAtBefore(String status, LocalDateTime time);

    Integer countByPavilionIdAndStatusIn(Integer pavilionId, List<String> statuses);

    Integer countByPavilionIdAndStatusInAndJoinedAtBefore(Integer pavilionId, List<String> statuses, LocalDateTime time);

    Integer countByStatusIn(List<String> statuses);

    Integer countByPavilionIdAndNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(Integer pavilionId, LocalDateTime time);

    Integer countByNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(LocalDateTime time);

    List<QueueTicket> findAllByPavilionIdAndStatusAndEnteredAtAfter(Integer pavilionId, String status, LocalDateTime time);

    List<QueueTicket> findAllByStatusAndEnteredAtAfter(String status, LocalDateTime time);

    List<QueueTicket> findAllByNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(LocalDateTime time);

    List<QueueTicket> findAllByPavilionIdAndStatusOrderByJoinedAtDesc(Integer pavilionId, String status);
}
