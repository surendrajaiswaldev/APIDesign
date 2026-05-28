package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent audit record of an exception thrown while serving a request.
 *
 * Inherits audit columns (createdAt/updatedAt/createdBy/updatedBy) from {@link BaseEntity};
 * createdBy is typically "system" since errors often happen outside an authenticated path.
 *
 * Populated by {@code ExceptionAuditService} from either the global exception handler or
 * the request interceptor's {@code afterCompletion} when status >= 500.
 */
@Entity
@Table(name = "APPLICATION_EXCEPTION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationException extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_exception_seq_gen")
    @SequenceGenerator(
        name = "app_exception_seq_gen",
        sequenceName = "APP_EXCEPTION_SEQ",
        allocationSize = 50)
    private Long id;

    @Column(name = "CORRELATION_ID", length = 64)
    private String correlationId;

    @Column(name = "HTTP_METHOD", length = 10)
    private String httpMethod;

    @Column(name = "REQUEST_PATH", length = 512)
    private String requestPath;

    @Column(name = "QUERY_STRING", length = 2048)
    private String queryString;

    @Lob
    @Column(name = "REQUEST_BODY")
    private String requestBody;

    @Column(name = "RESPONSE_STATUS")
    private Integer responseStatus;

    @Column(name = "EXCEPTION_CLASS", length = 256)
    private String exceptionClass;

    @Lob
    @Column(name = "EXCEPTION_MESSAGE")
    private String exceptionMessage;

    @Lob
    @Column(name = "STACK_TRACE")
    private String stackTrace;

    @Column(name = "USER_ID", length = 128)
    private String userId;

    @Column(name = "CLIENT_IP", length = 64)
    private String clientIp;

    @Column(name = "DURATION_MS")
    private Long durationMs;
}
