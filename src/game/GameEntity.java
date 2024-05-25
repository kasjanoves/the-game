package game;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Kasjanoves
 */
public enum GameEntity {
    ROCK("rock", 3),
    SCISSORS("scissors", 2),
    PAPER("paper", 1);
    private final String name;
    private final int strength;

    GameEntity(String name, int strength) {
        this.name = name;
        this.strength = strength;
    }

    public String getName() {
        return name;
    }

    public int compare(GameEntity other) {
        if (this == other) {
            return 0;
        }
        if (this == ROCK && other == PAPER) {
            return -1;
        }
        if (this == PAPER && other == ROCK) {
            return 1;
        }
        return this.strength - other.strength;
    }

    public static Set<String> getAvailableNames() {
        return Arrays.stream(GameEntity.values())
                .map(GameEntity::getName)
                .collect(Collectors.toSet());
    }

}
