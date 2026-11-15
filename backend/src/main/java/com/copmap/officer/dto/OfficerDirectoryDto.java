package com.copmap.officer.dto;

import java.io.Serializable;
import java.util.UUID;

public record OfficerDirectoryDto(
        UUID officerId,
        UUID mysqlUserId,
        String name,
        String policePost,
        String designation,
        String email,
        String role
) implements Serializable {}
