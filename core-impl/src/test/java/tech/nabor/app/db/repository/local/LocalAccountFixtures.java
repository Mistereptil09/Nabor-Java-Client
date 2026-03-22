package tech.nabor.app.db.repository.local;

import tech.nabor.api.model.local.LocalAccount;

public class LocalAccountFixtures {
    public static LocalAccount basicLocalAccount(String id, String email, String display_name) {
        return new LocalAccount(id, email, display_name, false, null);
    }
}
