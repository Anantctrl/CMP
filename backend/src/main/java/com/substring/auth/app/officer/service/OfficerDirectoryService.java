package com.substring.auth.app.officer.service;

import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.team.entity.TeamMembership;
import com.substring.auth.app.team.repository.TeamMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OfficerDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(OfficerDirectoryService.class);

    private final UserRepository userRepository;
    private final OfficerProfileRepository officerRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final CacheManager cacheManager;

    public OfficerDirectoryService(
            UserRepository userRepository,
            OfficerProfileRepository officerRepository,
            TeamMembershipRepository teamMembershipRepository,
            @Autowired(required = false) CacheManager cacheManager
    ) {
        this.userRepository = userRepository;
        this.officerRepository = officerRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = "officers_directory", key = "'all'")
    @SuppressWarnings("unchecked")
    public synchronized List<OfficerDirectoryDto> getAllOfficersCached() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("officers_directory");
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get("all");
                if (wrapper != null && wrapper.get() instanceof List) {
                    return (List<OfficerDirectoryDto>) wrapper.get();
                }
            }
        }

        log.info("[REDIS CACHE MISS] Loading officer directory from database and populating Redis...");
        List<User> users = userRepository.findAll();
        List<OfficerDirectoryDto> list = new ArrayList<>();

        Map<UUID, OfficerProfile> profileMap = new HashMap<>();
        for (OfficerProfile profile : officerRepository.findAll()) {
            if (profile.getMysqlUserId() != null) {
                profileMap.put(profile.getMysqlUserId(), profile);
            }
        }

        for (User user : users) {
            OfficerProfile profile = profileMap.get(user.getId());
            if (profile == null) {
                try {
                    OfficerProfile newProfile = new OfficerProfile();
                    newProfile.setMysqlUserId(user.getId());
                    newProfile.setOfficerName(user.getName());
                    String desig = (user.getPolicePost() != null && !user.getPolicePost().isBlank())
                            ? user.getPolicePost().trim()
                            : "Officer";
                    newProfile.setDesignation(desig);
                    profile = officerRepository.save(newProfile);
                    profileMap.put(user.getId(), profile);
                } catch (Exception e) {
                    profile = officerRepository.findByMysqlUserId(user.getId()).orElse(null);
                }
            }

            if (profile == null) {
                continue;
            }

            String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
            list.add(new OfficerDirectoryDto(
                    profile.getId(),
                    user.getId(),
                    user.getName() != null ? user.getName() : "Officer",
                    user.getPolicePost() != null ? user.getPolicePost() : "HQ Dispatch",
                    profile.getDesignation() != null ? profile.getDesignation() : "Officer",
                    user.getEmail(),
                    roleName
            ));
        }

        return list;
    }

    public List<OfficerDirectoryDto> getAvailableOfficersForTeam(UUID teamId) {
        List<OfficerDirectoryDto> allOfficers = getAllOfficersCached();
        List<TeamMembership> existingMembers = teamMembershipRepository.findAllByTeam_Id(teamId);
        Set<UUID> memberOfficerIds = new HashSet<>();
        for (TeamMembership m : existingMembers) {
            if (m.getOfficer() != null) {
                memberOfficerIds.add(m.getOfficer().getId());
            }
        }

        return allOfficers.stream()
                .filter(o -> !memberOfficerIds.contains(o.officerId()))
                .toList();
    }

    @CacheEvict(value = "officers_directory", allEntries = true)
    public void evictDirectoryCache() {
        log.info("[REDIS CACHE EVICTED] Officer directory cache cleared.");
    }
}
