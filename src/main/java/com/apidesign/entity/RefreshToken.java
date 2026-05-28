package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persisted, rotatable refresh token.
 *
 * <p>Only the SHA-256 hash of the actual token is stored — the raw value lives only with
 * the client. {@code familyId} chains all tokens issued from a single login; on reuse of
 * a revoked token, the entire family is invalidated (token-theft mitigation).
 * {@code replacedByHash} records which hash succeeded this one — gives auditors a
 * forward link through the rotation chain.
 */
@Entity
@Table(
    name = "REFRESH_TOKENS",
    indexes = {
        @Index(name = "IDX_REFRESH_TOKEN_FAMILY", columnList = "FAMILY_ID"),
        @Index(name = "IDX_REFRESH_TOKEN_USER", columnList = "USER_ID")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_token_seq_gen")
    @SequenceGenerator(
        name = "refresh_token_seq_gen",
        sequenceName = "REFRESH_TOKEN_SEQ",
        allocationSize = 50)
    private Long id;

    @Column(name = "TOKEN_HASH", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "REVOKED", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "REPLACED_BY_HASH", length = 64)
    private String replacedByHash;

    @Column(name = "FAMILY_ID", length = 36, nullable = false)
    private String familyId;
}
