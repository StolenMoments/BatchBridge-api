package org.jh.batchbridge.repository;

import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Job.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query("""
            SELECT j
            FROM Job j
            WHERE (:model IS NULL OR LOWER(j.model) = LOWER(:model))
              AND (:status IS NULL OR j.status = :status)
            """)
    Page<Job> findByModelAndStatus(
            @Param("model") String model,
            @Param("status") JobStatus status,
            Pageable pageable);
}
