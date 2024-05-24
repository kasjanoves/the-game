package game;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Kasjanoves
 */
@AllArgsConstructor
public enum GameEntity {

    ROCK("rock", 3),
    SCISSORS("scissors", 2),
    PAPER("paper", 1);

    @Getter
    private final String name;
    private final int strength;

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
