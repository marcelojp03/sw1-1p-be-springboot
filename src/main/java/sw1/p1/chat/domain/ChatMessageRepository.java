package sw1.p1.chat.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findByProcedureIdOrderByCreatedAtAsc(String procedureId);

    List<ChatMessage> findByOrganizationIdAndReceiverAreaId(String organizationId, String receiverAreaId);
}
