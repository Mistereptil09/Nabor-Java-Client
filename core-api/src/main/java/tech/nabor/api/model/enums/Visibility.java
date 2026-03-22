package tech.nabor.api.model.enums;

public enum Visibility {
    public_, friends, private_;

    public String toSqlValue() {
        return switch (this) {
            case public_  -> "public";
            case private_ -> "private";
            default       -> this.name();
        };
    }

    public static Visibility fromSqlValue(String value) {
        return switch (value) {
            case "public"  -> public_;
            case "private" -> private_;
            default        -> valueOf(value);
        };
    }
}