package com.example.soundattract.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PlayerStance {
    STANDING("standing"),
    SNEAKING("sneaking"),
    CRAWLING("crawling");

    private final String configName;

    private static final Map<String, PlayerStance> NAME_TO_STANCE_MAP = 
        Arrays.stream(values()).collect(Collectors.toMap(PlayerStance::getConfigName, Function.identity()));

    PlayerStance(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }

    public static Optional<PlayerStance> fromString(String name) {
        return Optional.ofNullable(NAME_TO_STANCE_MAP.get(name.toLowerCase()));
    }
}
