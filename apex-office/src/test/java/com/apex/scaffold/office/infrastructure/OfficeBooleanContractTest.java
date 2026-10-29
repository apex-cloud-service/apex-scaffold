package com.apex.scaffold.office.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.document.DocumentInfo;
import com.apex.scaffold.office.infrastructure.converter.CalendarEventConverter;
import com.apex.scaffold.office.infrastructure.converter.DocumentInfoConverter;
import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import com.apex.scaffold.office.infrastructure.po.DocumentInfoPO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

class OfficeBooleanContractTest {
    private final CalendarEventConverter calendar = Mappers.getMapper(CalendarEventConverter.class);
    private final DocumentInfoConverter documents = Mappers.getMapper(DocumentInfoConverter.class);

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void calendarFlagsAreNonNullAcrossPersistenceBoundary(Boolean value) {
        boolean expected = Boolean.TRUE.equals(value);
        CalendarEventPO po = calendar.toPO(
                CalendarEvent.builder().isAllDay(value).isRecurring(value).build());
        assertThat(po.getIsAllDay()).isEqualTo(expected);
        assertThat(po.getIsRecurring()).isEqualTo(expected);
        CalendarEventPO legacy = new CalendarEventPO();
        legacy.setIsAllDay(value);
        legacy.setIsRecurring(value);
        CalendarEvent restored = calendar.toEntity(legacy);
        assertThat(restored.getIsAllDay()).isEqualTo(expected);
        assertThat(restored.getIsRecurring()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void documentFlagsAreNonNullAcrossPersistenceBoundary(Boolean value) {
        boolean expected = Boolean.TRUE.equals(value);
        DocumentInfoPO po = documents.toPO(
                DocumentInfo.builder().isFolder(value).isArchived(value).build());
        assertThat(po.getIsFolder()).isEqualTo(expected);
        assertThat(po.getIsArchived()).isEqualTo(expected);
        DocumentInfoPO legacy = new DocumentInfoPO();
        legacy.setIsFolder(value);
        legacy.setIsArchived(value);
        DocumentInfo restored = documents.toEntity(legacy);
        assertThat(restored.getIsFolder()).isEqualTo(expected);
        assertThat(restored.getIsArchived()).isEqualTo(expected);
    }
}
