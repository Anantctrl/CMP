
package com.substring.auth.app.operations.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationLocationResponse(

        UUID id,

        UUID operationId,

        String name,

        String description,

        String address,

        Double latitude,

        Double longitude,

        Integer requiredManpower,

        Integer sequenceOrder,

        OffsetDateTime createdAt

) {}
