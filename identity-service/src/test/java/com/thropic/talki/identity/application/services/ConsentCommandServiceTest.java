package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.ConsentEventPublisher;
import com.thropic.talki.identity.application.ports.VoiceConsentRepository;
import com.thropic.talki.identity.domain.event.VoiceConsentEvent;
import com.thropic.talki.identity.domain.exception.ConsentNotFoundException;
import com.thropic.talki.identity.domain.exception.DuplicateConsentException;
import com.thropic.talki.identity.domain.exception.InvalidConsentPolicyException;
import com.thropic.talki.identity.domain.model.VoiceConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reglas de dominio de US33: aceptación, 409 duplicado, retiro idempotente,
 * compuerta por versión y eventos publicados.
 */
@ExtendWith(MockitoExtension.class)
class ConsentCommandServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private VoiceConsentRepository repository;

    @Mock
    private ConsentEventPublisher publisher;

    @InjectMocks
    private ConsentCommandService service;

    @BeforeEach
    void setUp() {
        // El servicio consulta el historial del usuario en cada operación.
        lenient().when(repository.findByUserId(USER_ID)).thenReturn(List.of());
        // El mock de save devuelve la entidad persistida (JPA la devuelve igual).
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- accept ----------

    @Test
    void accept_whenNoPreviousConsent_shouldSaveAndPublishRegistered() {
        service.accept(USER_ID, "v1");

        verify(repository).save(any(VoiceConsent.class));
        verify(publisher).publish(argThat(event ->
                VoiceConsentEvent.TYPE_REGISTERED.equals(event.getEventType())
                && event.getUserId().equals(USER_ID)
                && "v1".equals(event.getPolicyVersion())
                && VoiceConsent.SCOPE_VOICE_PROCESSING.equals(event.getScope())
                && event.getEventId() != null
                && event.getConsentId() != null));
    }

    @Test
    void accept_whenActiveConsentWithSameVersionExists_shouldThrow409() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(new VoiceConsent(USER_ID, "v1")));

        assertThatThrownBy(() -> service.accept(USER_ID, "v1"))
                .isInstanceOf(DuplicateConsentException.class);

        verify(repository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void accept_withDifferentVersion_shouldCreateNewRowInHistory() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(new VoiceConsent(USER_ID, "v1")));

        VoiceConsent accepted = service.accept(USER_ID, "v2");

        assertThat(accepted.getPolicyVersion()).isEqualTo("v2");
        verify(repository).save(any(VoiceConsent.class));
    }

    @Test
    void accept_overWithdrawnSameVersion_shouldCreateNewRowInsteadOf409() {
        VoiceConsent withdrawn = new VoiceConsent(USER_ID, "v1");
        withdrawn.withdraw(Instant.now());
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(withdrawn));

        assertThatCode(() -> service.accept(USER_ID, "v1")).doesNotThrowAnyException();

        verify(repository).save(any(VoiceConsent.class));
    }

    @Test
    void accept_withBlankPolicyVersion_shouldThrow400() {
        assertThatThrownBy(() -> service.accept(USER_ID, " "))
                .isInstanceOf(InvalidConsentPolicyException.class);
        assertThatThrownBy(() -> service.accept(USER_ID, null))
                .isInstanceOf(InvalidConsentPolicyException.class);

        verify(repository, never()).save(any());
    }

    // ---------- withdraw ----------

    @Test
    void withdraw_shouldMarkWithdrawnAtAndPublishWithdrawnEvent() {
        VoiceConsent consent = new VoiceConsent(USER_ID, "v1");
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(consent));

        Instant before = Instant.now();
        VoiceConsent result = service.withdraw(USER_ID, consent.getId().toString());

        assertThat(result.getWithdrawnAt()).isNotNull();
        verify(repository).save(consent);
        verify(publisher).publish(argThat(event ->
                VoiceConsentEvent.TYPE_WITHDRAWN.equals(event.getEventType())
                && event.getConsentId().equals(consent.getId().toString())));
        assertThat(before.isBefore(Instant.now())).isTrue();
    }

    @Test
    void withdraw_twice_shouldBeIdempotentAndPublishOnlyOneEvent() {
        VoiceConsent consent = new VoiceConsent(USER_ID, "v1");
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(consent));

        service.withdraw(USER_ID, consent.getId().toString());
        Instant firstWithdrawnAt = consent.getWithdrawnAt();
        VoiceConsent second = service.withdraw(USER_ID, consent.getId().toString());

        assertThat(second.getWithdrawnAt()).isEqualTo(firstWithdrawnAt);
        verify(repository, times(1)).save(consent);
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void withdraw_withConsentOfAnotherUser_shouldReturn404WithoutTouchingData() {
        UUID otherUsersConsentId = UUID.randomUUID(); // existe en BD pero es de otro usuario
        when(repository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.withdraw(USER_ID, otherUsersConsentId.toString()))
                .isInstanceOf(ConsentNotFoundException.class);

        verify(repository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void withdraw_withMalformedId_shouldReturn404() {
        assertThatThrownBy(() -> service.withdraw(USER_ID, "no-es-un-uuid"))
                .isInstanceOf(ConsentNotFoundException.class);
    }

    // ---------- isActiveFor (compuerta) ----------

    @Test
    void isActiveFor_withoutConsents_shouldBeFalse() {
        assertThat(service.isActiveFor(USER_ID, "v1")).isFalse();
    }

    @Test
    void isActiveFor_withActiveSameVersion_shouldBeTrue() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(new VoiceConsent(USER_ID, "v1")));

        assertThat(service.isActiveFor(USER_ID, "v1")).isTrue();
    }

    @Test
    void isActiveFor_withRequiredHigherVersion_shouldBeFalse() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(new VoiceConsent(USER_ID, "v1")));

        assertThat(service.isActiveFor(USER_ID, "v2")).isFalse();
    }

    @Test
    void isActiveFor_withActiveHigherVersionCoveringRequired_shouldBeTrue() {
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(new VoiceConsent(USER_ID, "v2")));

        assertThat(service.isActiveFor(USER_ID, "v1")).isTrue();
    }

    @Test
    void isActiveFor_withWithdrawnConsent_shouldBeFalse() {
        VoiceConsent withdrawn = new VoiceConsent(USER_ID, "v1");
        withdrawn.withdraw(Instant.now());
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(withdrawn));

        assertThat(service.isActiveFor(USER_ID, "v1")).isFalse();
    }

    // ---------- status ----------

    @Test
    void status_shouldReturnFullHistoryIncludingWithdrawn() {
        VoiceConsent active = new VoiceConsent(USER_ID, "v2");
        VoiceConsent withdrawn = new VoiceConsent(USER_ID, "v1");
        withdrawn.withdraw(Instant.now());
        when(repository.findByUserId(USER_ID)).thenReturn(List.of(withdrawn, active));

        assertThat(service.status(USER_ID)).hasSize(2)
                .anySatisfy(c -> assertThat(c.getWithdrawnAt()).isNotNull())
                .anySatisfy(c -> assertThat(c.getWithdrawnAt()).isNull());
    }
}
