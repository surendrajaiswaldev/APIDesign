package com.apidesign.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customises the OpenAPI 3 document. Beyond the JWT security scheme, this registers a
 * library of reusable {@link ApiResponse} components so controllers can reference them by
 * {@code $ref} instead of re-spelling the same response in every {@code @Operation}.
 *
 * <p>Reusable responses (keyed under {@code components.responses}):
 *
 * <ul>
 *   <li>{@code BadRequest} — RFC 7807 ProblemDetail, 400</li>
 *   <li>{@code Unauthorized} — 401</li>
 *   <li>{@code Forbidden} — 403</li>
 *   <li>{@code NotFound} — 404</li>
 *   <li>{@code Conflict} — 409</li>
 *   <li>{@code RateLimited} — 429</li>
 *   <li>{@code InternalError} — 500</li>
 * </ul>
 *
 * <p>Reference from a controller:
 *
 * <pre>{@code
 * @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
 * }</pre>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    public OpenAPI customOpenAPI() {
        Components components =
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addResponses("BadRequest", problemResponse("Request was malformed or failed validation"))
                .addResponses("Unauthorized", problemResponse("Authentication is required or has failed"))
                .addResponses("Forbidden", problemResponse("The authenticated principal lacks the required role"))
                .addResponses("NotFound", problemResponse("Resource not found"))
                .addResponses("Conflict", problemResponse("Conflicting state (e.g. optimistic-lock failure)"))
                .addResponses("RateLimited", problemResponse("Rate limit exceeded — retry after the Retry-After header"))
                .addResponses("InternalError", problemResponse("Unhandled server error"));

        return new OpenAPI()
            .info(
                new Info()
                    .title("Order Management System API")
                    .version("1.0.0")
                    .description(
                        "E-commerce order management API. Errors follow RFC 7807 (application/problem+json). "
                            + "Successful GETs return HAL (application/hal+json) with _links. "
                            + "POST/PATCH endpoints support the Idempotency-Key header."))
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .components(components);
    }

    private static ApiResponse problemResponse(String description) {
        // Schema kept open ({}) so SpringDoc can fall back to the actual ProblemDetail
        // generated representation at request time.
        Schema<?> schema = new Schema<>().type("object").description("RFC 7807 ProblemDetail");
        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(schema)));
    }
}
