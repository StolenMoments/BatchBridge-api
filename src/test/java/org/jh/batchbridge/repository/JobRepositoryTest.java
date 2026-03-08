package org.jh.batchbridge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.jh.batchbridge.domain.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("model과 status 조건으로 Job 목록을 필터링한다")
    void findByModelAndStatus_filtersByModelAndStatus() {
        jobRepository.save(Job.builder().name("job-1").model("gpt-4o").status(Job.JobStatus.PENDING).build());
        jobRepository.save(Job.builder().name("job-2").model("gpt-4o").status(Job.JobStatus.COMPLETED).build());
        jobRepository.save(Job.builder().name("job-3").model("claude-3-5-sonnet").status(Job.JobStatus.PENDING).build());

        Page<Job> filtered = jobRepository.findByModelAndStatus("GPT-4O", Job.JobStatus.PENDING, PageRequest.of(0, 10));

        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent()).extracting(Job::getName).containsExactly("job-1");
    }

    @Test
    @DisplayName("model과 status가 없으면 전체 Job 목록을 조회한다")
    void findByModelAndStatus_returnsAllWhenFiltersAreNull() {
        jobRepository.save(Job.builder().name("job-1").model("gpt-4o").status(Job.JobStatus.PENDING).build());
        jobRepository.save(Job.builder().name("job-2").model("claude-3-5-sonnet").status(Job.JobStatus.COMPLETED).build());

        Page<Job> all = jobRepository.findByModelAndStatus(null, null, PageRequest.of(0, 10));

        assertThat(all.getTotalElements()).isEqualTo(2);
    }
}
