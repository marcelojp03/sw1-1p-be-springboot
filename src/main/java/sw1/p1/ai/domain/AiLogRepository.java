package sw1.p1.ai.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiLogRepository extends MongoRepository<AiLog, String> {}
