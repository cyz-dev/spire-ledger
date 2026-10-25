import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglFiles;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tinycat.stats.BossRelic;
import tinycat.stats.Card;
import tinycat.stats.ClassStat;
import tinycat.stats.Mean;
import tinycat.stats.MetaScaling;
import tinycat.stats.Neow;
import tinycat.stats.Rate;
import tinycat.stats.Run;
import tinycat.tier.TierEntry;
import tinycat.tier.TierListBuilder;
import tinycat.tier.TierListGroup;
import tinycat.tier.TierListModel;
import tinycat.tier.TierListScope;
import tinycat.ui.MoreStatsScreen;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Offline driver: loads .run files with TinyCat's own Run parser and validity filter,
 * aggregates them with TinyCat's own ClassStat, and dumps every public statistic as rows.
 *
 * Our code only; it links against an unmodified TinyCat jar at runtime (no TinyCat source copied).
 * Must run with the working directory set to a game-root-like directory that contains runs/,
 * because Run reads optional .tinycat state via Gdx.files.local("runs/...").
 *
 * Usage: java -cp <tinycat.jar;desktop-1.0.jar;BaseMod.jar;.> TinyCatBaselineExport <slot> <out.json> <tinycat-version> <tinycat-commit>
 */
public class TinyCatBaselineExport {
    private static final String[] SCOPES = {"ALL", "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};

    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private int duplicateKeys = 0;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: TinyCatBaselineExport <slot> <out.json> <version> <commit>");
            System.exit(2);
        }
        int slot = Integer.parseInt(args[0]);
        File out = new File(args[1]);
        Gdx.files = new LwjglFiles();
        // BaseMod.initializeGson() does exactly `new GsonBuilder().create()`; set it without booting BaseMod.
        basemod.BaseMod.gson = new GsonBuilder().create();
        new TinyCatBaselineExport().run(slot, out, args[2], args[3]);
    }

    private void run(int slot, File out, String version, String commit) throws Exception {
        File runsDir = new File("runs").getAbsoluteFile();
        if (!runsDir.isDirectory()) {
            throw new IllegalStateException("no runs/ under working directory " + new File("").getAbsolutePath());
        }
        // Mirrors RunHistoryManager.loadData(): directory filter by save slot, parse, Run::valid, sorted.
        String prefix = slot + "_";
        Map<String, Integer> excluded = new TreeMap<>();
        Map<String, List<String>> excludedRuns = new TreeMap<>();
        List<Run> valid = new ArrayList<>();
        Map<Run, String> runIds = new IdentityHashMap<>();
        int scanned = 0;
        int trackedFiles = 0;
        File[] dirs = runsDir.listFiles();
        Arrays.sort(dirs);
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            String name = dir.getName();
            File[] files = dir.listFiles();
            Arrays.sort(files);
            for (File f : files) {
                if (f.getName().endsWith(".tinycat")) trackedFiles++;
            }
            boolean dirAccepted = !name.equals("gold_track") && (slot == 0
                    ? !name.contains("0_") && !name.contains("1_") && !name.contains("2_")
                    : name.contains(prefix));
            for (File f : files) {
                if (!f.getName().endsWith(".run")) continue;
                scanned++;
                String id = name + "/" + f.getName();
                if (!dirAccepted) {
                    count(excluded, excludedRuns, "directory_not_in_save_slot", id);
                    continue;
                }
                Optional<Run> parsed = Run.fromFile(new FileHandle(f));
                if (!parsed.isPresent()) {
                    count(excluded, excludedRuns, "parse_failed_or_not_base_character", id);
                    continue;
                }
                Run r = parsed.get();
                if (!r.valid()) {
                    count(excluded, excludedRuns, invalidReason(f), id);
                    continue;
                }
                valid.add(r);
                runIds.put(r, id);
            }
        }
        Collections.sort(valid);

        Map<String, List<String>> scopeRuns = new LinkedHashMap<>();
        for (String scope : SCOPES) {
            List<Run> subset = new ArrayList<>();
            for (Run r : valid) {
                if (scope.equals("ALL") || r.playerClass.name().equals(scope)) subset.add(r);
            }
            List<String> ids = new ArrayList<>();
            for (Run r : subset) ids.add(runIds.get(r));
            scopeRuns.put(scope, ids);
            // Same construction as MoreStatsScreen.getClassStats(): per class rotate=false, all classes rotate=true.
            ClassStat cs = new ClassStat(subset, scope.equals("ALL"));
            dump(scope, cs);
            dumpTiers(scope, cs);
        }

        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("table")))
                .thenComparing(m -> String.valueOf(m.get("scope")))
                .thenComparing(m -> String.valueOf(m.get("dims")))
                .thenComparing(m -> String.valueOf(m.get("metric"))));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("format", "tinycat-baseline");
        meta.put("format_version", 1);
        meta.put("source", "TinyCat " + version);
        meta.put("tinycat_commit", commit);
        meta.put("generated_by", "tools/tinycat-compare/TinyCatBaselineExport.java");
        meta.put("runs_dir", runsDir.getPath());
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("save_slot", slot);
        filters.put("ascension", "A20 only (Run.valid hard-codes isA20)");
        filters.put("modes", "exclude is_daily, is_special_run, is_endless (Run.valid)");
        filters.put("content", "exclude runs with ':' content or TinyCat ban lists, or without neow_bonus (Run.valid)");
        filters.put("date_range", "all months (stats screen default)");
        filters.put("include_abandons", true);
        filters.put("seed_filter", "ALL");
        filters.put("win_definition", "heart kill (Run.isHeartKill); every other run, abandons included, counts as loss");
        meta.put("filters", filters);
        meta.put("runs_scanned", scanned);
        meta.put("runs_included", valid.size());
        meta.put("excluded_by_reason", excluded);
        meta.put("excluded_runs", excludedRuns);
        meta.put("tinycat_state_files_found", trackedFiles);
        meta.put("run_ids", scopeRuns);
        meta.put("duplicate_row_keys", duplicateKeys);
        meta.put("row_count", rows.size());

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("meta", meta);
        doc.put("rows", rows);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
        out.getAbsoluteFile().getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(out.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(doc, w);
        }
        System.out.println("scanned=" + scanned + " included=" + valid.size() + " rows=" + rows.size()
                + " excluded=" + excluded + " duplicates=" + duplicateKeys);
    }

    private static void count(Map<String, Integer> c, Map<String, List<String>> ids, String reason, String id) {
        c.merge(reason, 1, Integer::sum);
        ids.computeIfAbsent(reason, k -> new ArrayList<>()).add(id);
    }

    /** Coarse reason for Run.valid()==false, read from the raw JSON (the first matching rule wins). */
    private static String invalidReason(File f) throws Exception {
        JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        if (bool(o, "is_special_run")) return "special_run";
        if (bool(o, "is_daily")) return "daily";
        if (bool(o, "is_endless")) return "endless";
        if (!bool(o, "is_ascension_mode") || o.get("ascension_level") == null || o.get("ascension_level").getAsInt() != 20) {
            return "not_a20";
        }
        JsonElement neow = o.get("neow_bonus");
        if (neow == null || neow.isJsonNull() || neow.getAsString().isEmpty()) return "missing_neow_bonus";
        for (String k : new String[]{"master_deck", "relics", "boss_relics", "damage_taken"}) {
            if (hasColonString(o.get(k))) return "mod_content_colon";
        }
        return "tinycat_ban_list";
    }

    private static boolean hasColonString(JsonElement e) {
        if (e == null || e.isJsonNull()) return false;
        if (e.isJsonPrimitive()) return e.getAsJsonPrimitive().isString() && e.getAsString().contains(":");
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (hasColonString(x)) return true;
            return false;
        }
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) if (hasColonString(x.getValue())) return true;
        return false;
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    /** Stand-in for the stats screen: TierListBuilder only needs getClassStat() and treats null dropdowns as defaults. */
    static final class OfflineScreen extends MoreStatsScreen {
        ClassStat stat;

        @Override
        public ClassStat getClassStat() {
            return stat;
        }
    }

    private static final TierListScope[] TIER_SCOPES = {
            TierListScope.ALL, TierListScope.NEOW, TierListScope.CAMPFIRE, TierListScope.CAMPFIRE_UPGRADE, TierListScope.SHOP};

    /** Tier lists and Plackett-Luce standardized rates, built by TinyCat's TierListBuilder with min sample 0. */
    private void dumpTiers(String scope, ClassStat cs) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        // The real constructor builds fonts and dropdowns; skip it, the builder reads no other state.
        OfflineScreen screen = (OfflineScreen) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(f.get(null), OfflineScreen.class);
        screen.stat = cs;
        for (TierListScope ts : TIER_SCOPES) {
            TierListModel model = TierListBuilder.build(screen, ts);
            for (TierListGroup g : model.groups) {
                String table = "tier:" + g.key;
                for (TierEntry e : g.entries) {
                    Map<String, String> dims = new LinkedHashMap<>();
                    dims.put("key", e.id);
                    emit(scope, table, dims, "rate", e.rate, e.picked, e.offered, e.offered);
                    emit(scope, table, dims, "tier", e.tier == null ? null : e.tier.name(), null, null, null);
                    if (e.standardized >= 0.0D) {
                        emit(scope, table, dims, "standardized", e.standardized, null, null, e.offered);
                        emit(scope, table, dims, "standardized_low", e.stdLow, null, null, null);
                        emit(scope, table, dims, "standardized_high", e.stdHigh, null, null, null);
                        emit(scope, table, dims, "tie_group", e.tieGroup, null, null, null);
                        if (e.rankOne >= 0.0D) emit(scope, table, dims, "p_rank_one", e.rankOne, null, null, null);
                    }
                }
            }
        }
    }

    // ---- dumping ----

    private void dump(String scope, ClassStat cs) throws Exception {
        for (Field f : ClassStat.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String table = f.getName();
            Object v = f.get(cs);
            if (table.equals("rotate")) continue;
            dumpValue(scope, table, new LinkedHashMap<>(), v);
        }
    }

    private void dumpValue(String scope, String table, Map<String, String> dims, Object v) throws Exception {
        if (v == null) {
            emit(scope, table, dims, "value", null, null, null, null);
        } else if (v instanceof Number) {
            Number n = (Number) v;
            boolean sentinel = table.equals("fastestTime") && n.longValue() == Integer.MAX_VALUE;
            emit(scope, table, dims, "value", sentinel ? null : n, null, null, null);
        } else if (v instanceof int[]) {
            int[] a = (int[]) v;
            for (int i = 0; i < a.length; i++) dumpValue(scope, table, with(dims, "act", String.valueOf(i)), a[i]);
        } else if (v instanceof double[]) {
            double[] a = (double[]) v;
            for (int i = 0; i < a.length; i++) dumpValue(scope, table, with(dims, "act", String.valueOf(i)), a[i]);
        } else if (v instanceof Rate) {
            Rate<?> r = (Rate<?>) v;
            int total = r.total();
            emit(scope, table, dims, "rate", total > 0 ? (double) r.win / total : null, r.win, total, total);
        } else if (v instanceof Mean) {
            Mean m = (Mean) v;
            int n = m.getSampleSize();
            emit(scope, table, dims, "mean", n > 0 ? m.mean() : null, n > 0 ? m.mean() * n : 0.0, n, n);
        } else if (v instanceof ClassStat.CampfireShare) {
            ClassStat.CampfireShare s = (ClassStat.CampfireShare) v;
            emit(scope, table, dims, "rate", s.visits > 0 ? (double) s.chosen / s.visits : null, s.chosen, s.visits, s.visits);
        } else if (v instanceof ClassStat.CampfireOption) {
            ClassStat.CampfireOption o = (ClassStat.CampfireOption) v;
            emit(scope, table, dims, "rate", o.available > 0 ? (double) o.chosen / o.available : null, o.chosen, o.available, o.available);
            emit(scope, table, dims, "baseline_percent", o.baseline, null, null, null);
            if (o.key.equals("RECALL")) {
                emit(scope, table, dims, "forced", o.forced, null, null, null);
                emit(scope, table, dims, "optional", o.optional, null, null, null);
            }
        } else if (v instanceof MetaScaling) {
            for (Field f : MetaScaling.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                dumpValue(scope, table, with(dims, "key", f.getName()), f.get(v));
            }
        } else if (v instanceof List) {
            List<?> list = (List<?>) v;
            if (!list.isEmpty() && list.get(0) instanceof Object[]) {
                // Choice blocks (Card[] / Neow[]) feed the Plackett-Luce models; report their count only.
                emit(scope, table, dims, "choice_sets", list.size(), null, null, null);
                return;
            }
            if (table.startsWith("cardChoices") || table.equals("neowChoices")) {
                emit(scope, table, dims, "choice_sets", 0, null, null, null);
                return;
            }
            for (Object item : list) dumpValue(scope, table, with(dims, "key", keyOf(item)), item);
        } else if (v instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) v;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                boolean actKey = e.getKey() instanceof Integer;
                String k = actKey ? String.valueOf(e.getKey()) : label(e.getKey());
                dumpValue(scope, table, with(dims, actKey ? "act" : "key", k), e.getValue());
            }
        } else if (v instanceof Boolean) {
            emit(scope, table, dims, "value", ((Boolean) v) ? 1 : 0, null, null, null);
        } else {
            throw new IllegalStateException("unhandled field type " + table + ": " + v.getClass());
        }
    }

    private static String keyOf(Object item) {
        if (item instanceof Rate) return label(((Rate<?>) item).what);
        if (item instanceof Mean) return ((Mean) item).what;
        if (item instanceof ClassStat.CampfireShare) {
            ClassStat.CampfireShare s = (ClassStat.CampfireShare) item;
            return (s.card ? "card:" : "action:") + label(s.what);
        }
        if (item instanceof ClassStat.CampfireOption) return ((ClassStat.CampfireOption) item).key;
        return label(item);
    }

    private static String label(Object what) {
        if (what instanceof Neow) {
            Neow n = (Neow) what;
            return n.bonus + " / " + n.cost;
        }
        if (what instanceof BossRelic) return ((BossRelic) what).name;
        if (what instanceof Card) return what.toString();
        return String.valueOf(what);
    }

    private static Map<String, String> with(Map<String, String> dims, String k, String v) {
        Map<String, String> d = new LinkedHashMap<>(dims);
        d.put(k, v);
        return d;
    }

    private void emit(String scope, String table, Map<String, String> dims, String metric,
                      Object value, Object numerator, Integer denominator, Integer sampleSize) {
        Map<String, String> d = new TreeMap<>(dims);
        String identity = table + "|" + scope + "|" + d + "|" + metric;
        int n = seen.merge(identity, 1, Integer::sum);
        if (n > 1) {
            duplicateKeys++;
            d.put("dup", String.valueOf(n));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("table", table);
        row.put("scope", scope);
        row.put("dims", d);
        row.put("metric", metric);
        row.put("value", value);
        row.put("numerator", numerator);
        row.put("denominator", denominator);
        row.put("sample_size", sampleSize);
        rows.add(row);
    }
}
