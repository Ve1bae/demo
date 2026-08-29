package com.example.video.model;

import java.util.Map;
import java.util.Set;

public record UserPreference(Set<Long> followedAuthorIds,
                             Map<String, Integer> interests,
                             Set<Long> viewedVideoIds) {
    public static UserPreference guest() {
        return new UserPreference(Set.of(), Map.of(), Set.of());
    }
}
