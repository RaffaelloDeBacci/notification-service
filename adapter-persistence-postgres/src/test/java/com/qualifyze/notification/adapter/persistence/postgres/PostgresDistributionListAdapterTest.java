package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DistributionListEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.DistributionListJpaRepository;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionList;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresDistributionListAdapterTest {

    @Mock
    private DistributionListJpaRepository distributionListJpaRepository;

    @Mock
    private AuditOutboxWriterPort auditOutboxWriterPort;

    @InjectMocks
    private PostgresDistributionListAdapter adapter;

    @Test
    void shouldReturnMappedTargetsWhenCustomerHasConfiguredTargets() {
        // Arrange
        when(distributionListJpaRepository.findByCustomerReferenceOrderById("customer-1")).thenReturn(List.of(
                new DistributionListEntity("customer-1", DistributionChannel.EMAIL, "ops@example.com"),
                new DistributionListEntity("customer-1", DistributionChannel.SLACK, "#alerts")
        ));

        // Act
        List<DistributionListTarget> result = adapter.findTargets(" customer-1 ");

        // Assert
        assertEquals(2, result.size());
        assertEquals(DistributionChannel.EMAIL, result.get(0).channel());
        assertEquals("ops@example.com", result.get(0).destination());
        assertEquals(DistributionChannel.SLACK, result.get(1).channel());
        assertEquals("#alerts", result.get(1).destination());
    }

    @Test
    void shouldReturnNoTargetsWhenCustomerHasNoConfiguredTargets() {
        // Arrange
        when(distributionListJpaRepository.findByCustomerReferenceOrderById("customer-2")).thenReturn(List.of());

        // Act
        List<DistributionListTarget> result = adapter.findTargets("customer-2");

        // Assert
        assertEquals(List.of(), result);
    }

    @Test
    void shouldWriteOnlyTheDiffAndCreateAddRemoveAuditEntriesWithCanonicalActor() {
        // Arrange
        DistributionList distributionList = new DistributionList("customer-1", Set.of(
                new DistributionListTarget(DistributionChannel.EMAIL, "new@example.com"),
                new DistributionListTarget(DistributionChannel.SLACK, "#ops")
        ));
        DistributionListEntity removedEntity = new DistributionListEntity("customer-1", DistributionChannel.EMAIL, "old@example.com");
        DistributionListEntity unchangedEntity = new DistributionListEntity("customer-1", DistributionChannel.SLACK, "#ops");
        when(distributionListJpaRepository.findByCustomerReferenceOrderById("customer-1"))
                .thenReturn(List.of(removedEntity, unchangedEntity));

        // Act
        adapter.replaceForCustomer(distributionList, "  alice  ");

        // Assert
        verify(distributionListJpaRepository).deleteAll(List.of(removedEntity));
        ArgumentCaptor<Iterable<DistributionListEntity>> savedTargetsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(distributionListJpaRepository).saveAll(savedTargetsCaptor.capture());
        List<DistributionListEntity> savedTargets = toList(savedTargetsCaptor.getValue());
        assertEquals(1, savedTargets.size());
        assertEquals("new@example.com", savedTargets.getFirst().getNotificationTarget());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditOutboxWriterPort, org.mockito.Mockito.times(2)).publish(auditCaptor.capture());
        List<AuditEvent> events = auditCaptor.getAllValues();
        AuditEvent removeEvent = events.stream()
                .filter(event -> "notification.distribution-list.target-removed".equals(event.eventType()))
                .findFirst()
                .orElseThrow();
        AuditEvent addEvent = events.stream()
                .filter(event -> "notification.distribution-list.target-added".equals(event.eventType()))
                .findFirst()
                .orElseThrow();

        assertEquals("alice", removeEvent.actor());
        assertEquals("customer-1", removeEvent.aggregateId());
        assertEquals("{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"old@example.com\"}", removeEvent.payloadJson());

        assertEquals("alice", addEvent.actor());
        assertEquals("customer-1", addEvent.aggregateId());
        assertEquals("{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"new@example.com\"}", addEvent.payloadJson());
    }

    @Test
    void shouldUseDefaultActorWhenChangedByIsBlank() {
        // Arrange
        DistributionList distributionList = new DistributionList("customer-2", Set.of(
                new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com")
        ));
        when(distributionListJpaRepository.findByCustomerReferenceOrderById("customer-2")).thenReturn(List.of());

        // Act
        adapter.replaceForCustomer(distributionList, " ");

        // Assert
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditOutboxWriterPort).publish(auditCaptor.capture());
        assertEquals("notification-service", auditCaptor.getValue().actor());
        assertEquals("notification.distribution-list.target-added", auditCaptor.getValue().eventType());
    }

    @Test
    void shouldEscapeAndRoundTripAuditPayloadWithQuotesAndBackslashes() throws Exception {
        // Arrange
        DistributionList distributionList = new DistributionList("customer-3", Set.of(
                new DistributionListTarget(DistributionChannel.EMAIL, "ops\\\"@example.com")
        ));
        when(distributionListJpaRepository.findByCustomerReferenceOrderById("customer-3")).thenReturn(List.of(
                new DistributionListEntity("customer-3", DistributionChannel.EMAIL, "before\\\\\"@example.com")
        ));

        // Act
        adapter.replaceForCustomer(distributionList, "alice");

        // Assert
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditOutboxWriterPort, org.mockito.Mockito.times(2)).publish(auditCaptor.capture());
        ObjectMapper objectMapper = new ObjectMapper();
        AuditEvent removeEvent = auditCaptor.getAllValues().stream()
                .filter(audit -> "notification.distribution-list.target-removed".equals(audit.eventType()))
                .findFirst()
                .orElseThrow();
        AuditEvent addEvent = auditCaptor.getAllValues().stream()
                .filter(audit -> "notification.distribution-list.target-added".equals(audit.eventType()))
                .findFirst()
                .orElseThrow();
        assertEquals("before\\\\\"@example.com", objectMapper.readTree(removeEvent.payloadJson()).get("notificationTarget").asText());
        assertEquals("ops\\\"@example.com", objectMapper.readTree(addEvent.payloadJson()).get("notificationTarget").asText());
    }

    private static List<DistributionListEntity> toList(Iterable<DistributionListEntity> iterable) {
        return ((List<DistributionListEntity>) iterable);
    }

    private static Object getField(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot read field " + name, exception);
        }
    }
}
