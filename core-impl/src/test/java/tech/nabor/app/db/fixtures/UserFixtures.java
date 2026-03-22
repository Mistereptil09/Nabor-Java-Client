package tech.nabor.app.db.fixtures;

import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.api.model.user.User;

import java.time.Instant;

public class UserFixtures {
    public static User basicUser(String id, String email) {
        return new User(
                id, "Antonio", "B", email, "hash", null,
                null, "neighbourhood-1", Visibility.public_,
                null, MessagePolicy.open, "fr", null, null,
                UserRole.resident, null, null,
                Instant.now(), null, null
        );
    }

    public static User userWithRole(String id, String email, UserRole role) {
        return new User(
                id, "Antonio", "B", email, "hash", null,
                null, "neighbourhood-1", Visibility.public_,
                null, MessagePolicy.open, "fr", null, null,
                role, null, null,
                Instant.now(), null, null
        );
    }
}
