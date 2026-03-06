package org.jh.batchbridge.repository;

import org.jh.batchbridge.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {
    List<Result> findByJobId(Long jobId);
    List<Result> findByChunkId(Long chunkId);
}
