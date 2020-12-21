package robotlib.util;

import java.util.HashMap;
import java.util.Map;

public class TelemetryData {
    private String prefix;
    private Map<String, Object> data;

    public TelemetryData(String prefix) {
        this.prefix = prefix;
        this.data = new HashMap<>();
    }

    public void put(String label, Object value) {
        data.put(prefix + ": " + label, value);
    }

    public Map<String, Object> getData() {
        return data;
    }
}
