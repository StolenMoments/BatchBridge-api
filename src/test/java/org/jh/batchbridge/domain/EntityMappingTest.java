package org.jh.batchbridge.domain;

import org.jh.batchbridge.repository.ChunkRepository;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
public class EntityMappingTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Test
    @DisplayName("Job, Chunk, Result 엔티티 저장 및 연관관계 확인")
    void testEntityMapping() {
        // Given
        Job job = Job.builder()
                .name("test_batch.xlsx")
                .status(Job.JobStatus.PENDING)
                .totalRows(10)
                .build();
        jobRepository.save(job);

        Chunk chunk = Chunk.builder()
                .job(job)
                .provider("Anthropic")
                .model("claude-3-5-sonnet-20241022")
                .status(Chunk.ChunkStatus.CREATED)
                .rowCount(5)
                .build();
        chunkRepository.save(chunk);

        Result result = Result.builder()
                .job(job)
                .chunk(chunk)
                .rowIdentifier("row-1")
                .prompt("Hello AI")
                .model("claude-3-5-sonnet-20241022")
                .status(Result.ResultStatus.SUCCESS)
                .resultText("Hello Human")
                .build();
        resultRepository.save(result);

        // When
        Job savedJob = jobRepository.findById(job.getId()).orElseThrow();
        List<Chunk> savedChunks = chunkRepository.findByJobId(savedJob.getId());
        List<Result> savedResults = resultRepository.findByChunkId(chunk.getId());

        // Then
        assertThat(savedJob.getName()).isEqualTo("test_batch.xlsx");
        assertThat(savedChunks).hasSize(1);
        assertThat(savedChunks.get(0).getProvider()).isEqualTo("Anthropic");
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getRowIdentifier()).isEqualTo("row-1");
        assertThat(savedResults.get(0).getJob().getId()).isEqualTo(savedJob.getId());
    }
}
