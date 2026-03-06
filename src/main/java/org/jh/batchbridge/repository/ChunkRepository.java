package org.jh.batchbridge.repository;

import org.jh.batchbridge.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByJobId(Long jobId);
    List<Chunk> findByStatus(Chunk.ChunkStatus status);
}
