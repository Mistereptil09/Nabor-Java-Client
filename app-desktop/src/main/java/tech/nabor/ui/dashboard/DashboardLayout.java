package tech.nabor.ui.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.nabor.api.PluginContext;

import java.util.*;

/** Loads and saves tile layout from plugin_config. */
public class DashboardLayout {

    private static final ObjectMapper mapper = new ObjectMapper();

    private DashboardLayout() {}

    public static List<TilePlacement> load(PluginContext ctx) {
        try {
            String userId = ctx.getConnectedUser().getUserId();
            var val = ctx.getDb().pluginConfigs().getValue(userId, "dashboard", "layout");
            if (val.isPresent() && !val.get().isBlank()) {
                List<Map<String, Object>> raw = mapper.readValue(val.get(),
                        new TypeReference<List<Map<String, Object>>>() {});
                List<TilePlacement> list = new ArrayList<>();
                for (Map<String, Object> m : raw) {
                    list.add(new TilePlacement(
                            (String) m.get("id"),
                            ((Number) m.get("col")).intValue(),
                            ((Number) m.get("row")).intValue(),
                            m.containsKey("colSpan") ? ((Number) m.get("colSpan")).intValue() : 1));
                }
                return list;
            }
        } catch (Exception ignored) {}
        return defaultLayout();
    }

    public static void save(PluginContext ctx, List<TilePlacement> layout) {
        try {
            String userId = ctx.getConnectedUser().getUserId();
            List<Map<String, Object>> raw = new ArrayList<>();
            for (TilePlacement p : layout) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.tileId());
                m.put("col", p.col());
                m.put("row", p.row());
                m.put("colSpan", p.colSpan());
                raw.add(m);
            }
            ctx.getDb().pluginConfigs().setValue(userId, "dashboard", "layout",
                    mapper.writeValueAsString(raw));
        } catch (Exception ignored) {}
    }

    private static List<TilePlacement> defaultLayout() {
        return List.of(
                new TilePlacement("kpi-incidents", 0, 0, 1),
                new TilePlacement("kpi-events",    1, 0, 1),
                new TilePlacement("sync-quick",    2, 0, 1),
                new TilePlacement("chart-trend",   0, 1, 2),
                new TilePlacement("chart-status",  2, 1, 2),
                new TilePlacement("table-recent",  0, 2, 2),
                new TilePlacement("chart-severity",2, 2, 2));
    }
}
