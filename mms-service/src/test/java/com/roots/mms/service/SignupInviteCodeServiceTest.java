package com.roots.mms.service;

import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.SignupInviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupInviteCodeServiceTest {

    private SignupInviteCodeRepository repo;
    private SignupInviteCodeService service;

    @BeforeEach
    void setUp() {
        repo = mock(SignupInviteCodeRepository.class);
        service = new SignupInviteCodeService(repo);
    }

    // ── create ────────────────────────────────────────────────────────

    @Test
    void create_blankCode_throws() {
        assertThatThrownBy(() -> service.create("  ", "d", null, null, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("required");
        verify(repo, never()).save(any());
    }

    @Test
    void create_nullCode_throws() {
        assertThatThrownBy(() -> service.create(null, "d", null, null, "admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_duplicate_throws() {
        when(repo.existsByCode("ABC")).thenReturn(true);

        assertThatThrownBy(() -> service.create("abc", null, null, null, "admin"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_zeroMaxUses_throws() {
        when(repo.existsByCode("ABC")).thenReturn(false);

        assertThatThrownBy(() -> service.create("abc", null, 0, null, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 1");
        verify(repo, never()).save(any());
    }

    @Test
    void create_negativeMaxUses_throws() {
        when(repo.existsByCode("ABC")).thenReturn(false);

        assertThatThrownBy(() -> service.create("abc", null, -1, null, "admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_normalizesCodeAndSetsDefaults() {
        when(repo.existsByCode("ABC")).thenReturn(false);
        when(repo.save(any(SignupInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
        SignupInviteCode created = service.create("  abc  ", "desc", 5, expires, "admin@example.com");

        assertThat(created.getCode()).isEqualTo("ABC");
        assertThat(created.getDescription()).isEqualTo("desc");
        assertThat(created.getMaxUses()).isEqualTo(5);
        assertThat(created.getExpiresAt()).isEqualTo(expires);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getUsedCount()).isZero();
        assertThat(created.getCreatedBy()).isEqualTo("admin@example.com");
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void create_unlimitedUses_allowedWhenNull() {
        when(repo.existsByCode("ABC")).thenReturn(false);
        when(repo.save(any(SignupInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupInviteCode created = service.create("abc", null, null, null, "admin");

        assertThat(created.getMaxUses()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────

    @Test
    void update_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id.toString(), true, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_partialPatch_onlyAppliesNonNullArgs() {
        UUID id = UUID.randomUUID();
        SignupInviteCode existing = SignupInviteCode.builder()
                .id(id).code("ABC").active(true).maxUses(5).usedCount(2)
                .description("old desc").build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(SignupInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupInviteCode updated = service.update(id.toString(), false, null, null, null);

        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getMaxUses()).isEqualTo(5);              // unchanged
        assertThat(updated.getDescription()).isEqualTo("old desc"); // unchanged
    }

    @Test
    void update_maxUsesBelowOne_throws() {
        UUID id = UUID.randomUUID();
        SignupInviteCode existing = SignupInviteCode.builder().id(id).usedCount(0).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id.toString(), null, 0, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void update_maxUsesBelowUsedCount_throws() {
        UUID id = UUID.randomUUID();
        SignupInviteCode existing = SignupInviteCode.builder().id(id).usedCount(7).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id.toString(), null, 5, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("usedCount");
    }

    @Test
    void update_maxUsesEqualToUsedCount_allowed() {
        UUID id = UUID.randomUUID();
        SignupInviteCode existing = SignupInviteCode.builder().id(id).usedCount(5).maxUses(10).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(SignupInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupInviteCode updated = service.update(id.toString(), null, 5, null, null);

        assertThat(updated.getMaxUses()).isEqualTo(5);
    }

    @Test
    void update_setExpiresAtAndDescription() {
        UUID id = UUID.randomUUID();
        SignupInviteCode existing = SignupInviteCode.builder().id(id).usedCount(0).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(SignupInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant expires = Instant.now().plusSeconds(3600);
        SignupInviteCode updated = service.update(id.toString(), null, null, expires, "new desc");

        assertThat(updated.getExpiresAt()).isEqualTo(expires);
        assertThat(updated.getDescription()).isEqualTo("new desc");
    }

    // ── delete ────────────────────────────────────────────────────────

    @Test
    void delete_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_existing_callsDeleteById() {
        UUID id = UUID.randomUUID();
        when(repo.existsById(id)).thenReturn(true);

        service.delete(id.toString());

        verify(repo).deleteById(id);
    }

    // ── requireRedeemable ─────────────────────────────────────────────

    @Test
    void requireRedeemable_blankCode_throws() {
        assertThatThrownBy(() -> service.requireRedeemable("  "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requireRedeemable_nullCode_throws() {
        assertThatThrownBy(() -> service.requireRedeemable(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requireRedeemable_unknownCode_throwsRecognized() {
        when(repo.findByCode("ABC")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireRedeemable("abc"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not recognized");
    }

    @Test
    void requireRedeemable_inactive_throwsNoLongerActive() {
        when(repo.findByCode("ABC")).thenReturn(Optional.of(
                SignupInviteCode.builder().code("ABC").active(false).build()));

        assertThatThrownBy(() -> service.requireRedeemable("abc"))
                .hasMessageContaining("no longer active");
    }

    @Test
    void requireRedeemable_expired_throwsExpired() {
        when(repo.findByCode("ABC")).thenReturn(Optional.of(
                SignupInviteCode.builder().code("ABC").active(true)
                        .expiresAt(Instant.now().minusSeconds(60)).build()));

        assertThatThrownBy(() -> service.requireRedeemable("abc"))
                .hasMessageContaining("expired");
    }

    @Test
    void requireRedeemable_exhausted_throwsLimit() {
        when(repo.findByCode("ABC")).thenReturn(Optional.of(
                SignupInviteCode.builder().code("ABC").active(true)
                        .maxUses(2).usedCount(2).build()));

        assertThatThrownBy(() -> service.requireRedeemable("abc"))
                .hasMessageContaining("usage limit");
    }

    @Test
    void requireRedeemable_nullUsedCountTreatedAsZero() {
        // usedCount=null with maxUses=1 should be treated as 0 (still redeemable).
        SignupInviteCode invite = SignupInviteCode.builder().code("ABC").active(true)
                .maxUses(1).usedCount(null).build();
        when(repo.findByCode("ABC")).thenReturn(Optional.of(invite));

        assertThat(service.requireRedeemable("abc")).isSameAs(invite);
    }

    @Test
    void requireRedeemable_unlimitedAndFutureExpiry_returnsInvite() {
        SignupInviteCode invite = SignupInviteCode.builder().code("ABC").active(true)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repo.findByCode("ABC")).thenReturn(Optional.of(invite));

        assertThat(service.requireRedeemable("abc")).isSameAs(invite);
    }

    // ── consume ───────────────────────────────────────────────────────

    @Test
    void consume_increments_andSaves() {
        SignupInviteCode invite = SignupInviteCode.builder()
                .code("ABC").active(true).maxUses(5).usedCount(2).build();
        when(repo.findByCode("ABC")).thenReturn(Optional.of(invite));

        service.consume("abc");

        var captor = forClass(SignupInviteCode.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUsedCount()).isEqualTo(3);
    }

    @Test
    void consume_nullUsedCount_startsAtOne() {
        SignupInviteCode invite = SignupInviteCode.builder()
                .code("ABC").active(true).usedCount(null).build();
        when(repo.findByCode("ABC")).thenReturn(Optional.of(invite));

        service.consume("abc");

        assertThat(invite.getUsedCount()).isEqualTo(1);
        verify(repo).save(invite);
    }

    @Test
    void consume_nonRedeemable_throwsAndDoesNotSave() {
        when(repo.findByCode("ABC")).thenReturn(Optional.of(
                SignupInviteCode.builder().code("ABC").active(false).build()));

        assertThatThrownBy(() -> service.consume("abc"))
                .isInstanceOf(BusinessRuleException.class);
        verify(repo, never()).save(any());
    }

    // ── listAll ───────────────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepo() {
        SignupInviteCode a = SignupInviteCode.builder().code("A").build();
        when(repo.findAll()).thenReturn(java.util.List.of(a));

        assertThat(service.listAll()).containsExactly(a);
    }
}
