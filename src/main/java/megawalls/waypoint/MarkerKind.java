package megawalls.waypoint;

public enum MarkerKind {
    HERE("h", "Here", 0x55FFFF),
    WAYPOINT("w", "Waypoint", 0xFFFFFF);

    private final String wireId;
    private final String label;
    private final int rgb;

    MarkerKind(String wireId, String label, int rgb) {
        this.wireId = wireId;
        this.label = label;
        this.rgb = rgb;
    }

    public String getWireId() {
        return wireId;
    }

    public String getLabel() {
        return label;
    }

    public int getRgb() {
        return rgb;
    }

    public static MarkerKind fromWireId(String wireId) {
        if (wireId == null) {
            return null;
        }

        for (MarkerKind kind : values()) {
            if (kind.wireId.equals(wireId)) {
                return kind;
            }
        }

        return null;
    }
}
