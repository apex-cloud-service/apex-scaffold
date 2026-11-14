package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessagePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffEmployeeMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageRecipientMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessagePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageRecipientPO;
import com.apex.scaffold.office.off.infrastructure.persistence.repository.MessageDeliveryRepositoryImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.test.StepVerifier;

/**
 * 使用真实 H2 JDBC 事务验证发送原子性，Mapper 替身只桥接到相同数据源。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageDeliveryTransactionTest {
    /** 第二个合法接收人触发数据库约束失败时，消息及第一个接收人均回滚。 */
    @Test
    void rollsBackMessageAndRecipientsOnFailure() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:messageRollback;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE rollback_message (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE rollback_recipient (user_id BIGINT CHECK (user_id > 0))");
        var messages = mock(OffMessageMapper.class);
        var recipients = mock(OffMessageRecipientMapper.class);
        var converter = mock(OffMessagePoConverter.class);
        var employees = mock(OffEmployeeMapper.class);
        var entity = new OffMessageEntity();
        entity.setTenantId("a");
        var po = new OffMessagePO();
        po.setId(99L);
        when(converter.toPo(entity)).thenReturn(po);
        when(messages.insert(any(OffMessagePO.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            return jdbc.update("INSERT INTO rollback_message VALUES (?)", 99L);
        });
        var firstUser = new OffEmployeePO();
        firstUser.setUserId(7L);
        var secondUser = new OffEmployeePO();
        secondUser.setUserId(8L);
        when(employees.selectList(any(Wrapper.class))).thenAnswer(call -> {
            assertThat(TenantContext.currentTenantId()).contains("a");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            return List.of(firstUser, secondUser);
        });
        var repository = new MessageDeliveryRepositoryImpl(
                messages, recipients, converter, new DataSourceTransactionManager(source), employees);
        when(recipients.insert(any(OffMessageRecipientPO.class))).thenAnswer(call -> {
            OffMessageRecipientPO recipient = call.getArgument(0);
            assertThat(TenantContext.currentTenantId()).contains("a");
            return jdbc.update(
                    "INSERT INTO rollback_recipient VALUES (?)",
                    recipient.getRecipientId() == 8L ? -1L : recipient.getRecipientId());
        });
        StepVerifier.create(repository.save(entity, List.of(7L, 8L)).contextWrite(TenantContext.withTenant("a")))
                .expectError()
                .verify();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rollback_message", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rollback_recipient", Integer.class))
                .isZero();
        when(recipients.insert(any(OffMessageRecipientPO.class))).thenAnswer(call -> {
            OffMessageRecipientPO recipient = call.getArgument(0);
            return jdbc.update("INSERT INTO rollback_recipient VALUES (?)", recipient.getRecipientId());
        });
        StepVerifier.create(repository.save(entity, List.of(7L, 8L)).contextWrite(TenantContext.withTenant("a")))
                .expectNext(entity)
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rollback_message", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rollback_recipient", Integer.class))
                .isEqualTo(2);
    }

    /** 任一收件人没有本租户有效员工绑定时，禁止写入消息及接收人。 */
    @Test
    void rejectsUnresolvedRecipientsBeforeWriting() {
        var messages = mock(OffMessageMapper.class);
        var recipients = mock(OffMessageRecipientMapper.class);
        var converter = mock(OffMessagePoConverter.class);
        var employees = mock(OffEmployeeMapper.class);
        var source = new DriverManagerDataSource("jdbc:h2:mem:messageOwnership", "sa", "");
        var repository = new MessageDeliveryRepositoryImpl(
                messages, recipients, converter, new DataSourceTransactionManager(source), employees);
        var firstUser = new OffEmployeePO();
        firstUser.setUserId(7L);
        when(employees.selectList(any(Wrapper.class))).thenReturn(List.of(firstUser));
        var entity = new OffMessageEntity();
        entity.setTenantId("a");
        StepVerifier.create(repository.save(entity, List.of(7L, 8L)).contextWrite(TenantContext.withTenant("a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(messages, recipients, converter);
    }

    /** 实体租户不匹配可信 Reactor 上下文时不得访问 Mapper。 */
    @Test
    void rejectsMismatchedTenantBeforePersistence() {
        var messages = mock(OffMessageMapper.class);
        var recipients = mock(OffMessageRecipientMapper.class);
        var converter = mock(OffMessagePoConverter.class);
        var employees = mock(OffEmployeeMapper.class);
        var repository = new MessageDeliveryRepositoryImpl(
                messages,
                recipients,
                converter,
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                employees);
        var entity = new OffMessageEntity();
        entity.setTenantId("b");
        StepVerifier.create(repository.save(entity, List.of(7L)).contextWrite(TenantContext.withTenant("a")))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(messages, recipients, converter, employees);
    }
}
