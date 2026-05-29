package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persisted record of a previously-processed POST/PATCH carrying an {@code Idempotency-Key}
 * header. Lookup by {@link #idempotencyKey} short-circuits the controller chain and returns
 * the original response — provided the request body still hashes to the same value.
 *
 * <p>Mismatched bodies for the same key surface as 422 (semantically: "the key was reused
 * with different content") rather than silently returning the cached response.
 *
 * <p>{@code responseBody} is stored verbatim (post-serialisation) so the replay is
 * byte-identical to the original successful call.
 */
@Entity
@Table(
    name = "IDEMPOTENCY_KEY",
    indexes = {
        @Index(name = "IDX_IDEMPOTENCY_KEY", columnList = "IDEMPOTENCY_KEY", unique = true),
        @Index(name = "IDX_IDEMPOTENCY_USER", columnList = "USER_ID")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class IdempotencyKey extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idempotency_key_seq_gen")
    @SequenceGenerator(
        name = "idempotency_key_seq_gen",
        sequenceName = "IDEMPOTENCY_KEY_SEQ",
        allocationSize = 50)
    private Long id;

    @Column(name = "IDEMPOTENCY_KEY", length = 64, nullable = false, unique = true)
    private String idempotencyKey;

    /** SHA-256 hex of the request body. Used to detect mismatched reuse of the same key. */
    @Column(name = "REQUEST_HASH", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "RESPONSE_STATUS", nullable = false)
    private Integer responseStatus;

    @Lob
    @Column(name = "RESPONSE_BODY")
    private String responseBody;

    @Column(name = "ENDPOINT", length = 256)
    private String endpoint;

    @Column(name = "USER_ID", length = 128)
    private String userId;
}
