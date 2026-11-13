package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.OperationLocation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationLocationRepository
        extends JpaRepository<OperationLocation, UUID> {

    List<OperationLocation> findAllByOperation_IdOrderBySequenceOrderAsc(
            UUID operationId
    );
}
