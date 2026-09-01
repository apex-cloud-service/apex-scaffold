package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.infrastructure.persistence.converter.JobLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.JobLogMapper;
import org.junit.jupiter.api.Test;

class JobLogRepositoryImplTest {

    @Test
    void rejectsNonPositiveLogLimit() {
        JobLogMapper mapper = mock(JobLogMapper.class);
        JobLogPoConverter converter = mock(JobLogPoConverter.class);
        JobLogRepositoryImpl repository = new JobLogRepositoryImpl(mapper, converter);

        assertThatThrownBy(() -> repository.findLatestByJobId(1L, 0).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("日志条数必须大于0");
        verifyNoInteractions(mapper);
    }
}
