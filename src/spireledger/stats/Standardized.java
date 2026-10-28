package spireledger.stats;

import java.util.*;

/**
 * Standardized rate: TinyCat's Plackett-Luce estimate over whole reward screens (ported unchanged in behaviour).
 *
 * <p>Each screen is one choice set S and {@code P(pick i | S) = s_i / sum(S)}; a screen won against strong
 * offers says more than one won against weak offers. The observed rate ("picked / offered") cannot separate
 * the two, which is why both views exist and must not be subtracted.</p>
 *
 * <p>The fit is the same weighted MM iteration TinyCat uses: weakly informative priors (one imaginary win,
 * 0.01 imaginary appearance), geometric-mean normalization each round, 1e-9 relative tolerance, 2000 rounds.
 * Skip and Singing Bowl merge into {@link #SKIP}: both mean the screen was passed, and treating them as two
 * options would split one behaviour into two rows. SKIP stays in the universe as an opponent but is never
 * reported. Bootstrap intervals are deliberately not ported: they cost 1000 extra fits and the stats screen
 * runs on the render thread.</p>
 */
public final class Standardized {
    public static final String VERSION = "standardized-v1";
    public static final String SKIP = "SKIP";
    static final double PRIOR_WINS = 1.0D;
    static final double PRIOR_SURVIVAL = 0.01D;
    static final double TOL = 1.0e-9D;
    static final double FLOOR = 1.0e-12D;
    static final int MAX_ITER = 2000;

    /** One act's shares: card to strength share in percent, summing to 100 over the reported cards. */
    public static final class Act {
        public final boolean available;
        public final Map<String, Double> share;
        public final int screens, cards;
        Act(boolean available, Map<String, Double> share, int screens, int cards) {
            this.available = available;
            this.share = share;
            this.screens = screens;
            this.cards = cards;
        }
    }

    /** Per act, from every confirmed reward screen of the selection; acts without screens are absent. */
    public static Map<Integer, Act> compute(List<Run> runs) {
        Map<Integer, List<String[]>> byAct = new TreeMap<Integer, List<String[]>>();
        for (Run r : runs) {
            if (!r.screens.known()) {
                continue;
            }
            for (Run.Screen s : r.screens.value) {
                if ("known_automation".equals(s.actor)) {
                    continue;
                }
                String win;
                if (s.kind.equals("card")) {
                    if (!s.offered.contains(s.picked)) {
                        continue;
                    }
                    win = s.picked;
                } else {
                    win = SKIP;
                }
                List<String> block = new ArrayList<String>();
                block.add(win);
                for (String card : s.offered) {
                    if (!card.equals(win)) {
                        block.add(card);
                    }
                }
                // Skip is always an option on a reward screen, whether or not it was taken.
                if (!win.equals(SKIP)) {
                    block.add(SKIP);
                }
                byAct.computeIfAbsent(Integer.valueOf(s.act), k -> new ArrayList<String[]>()).add(block.toArray(new String[0]));
            }
        }
        Map<Integer, Act> out = new TreeMap<Integer, Act>();
        for (Map.Entry<Integer, List<String[]>> e : byAct.entrySet()) {
            out.put(e.getKey(), fit(e.getValue()));
        }
        return out;
    }

    /** One Neow screen per run: the picked blessing first, then the skipped options; runs without a skipped log carry no evidence. */
    public static Act computeNeow(List<Run> runs) {
        List<String[]> blocks = new ArrayList<String[]>();
        for (Run r : runs) {
            List<String> offers = r.neowOffers.or(null);
            String picked = r.neow.or(null);
            if (offers == null || picked == null) {
                continue;
            }
            List<String> block = new ArrayList<String>();
            block.add(picked);
            for (String id : offers) {
                if (id != null && !id.isEmpty() && !id.equals(picked)) {
                    block.add(id);
                }
            }
            if (block.size() >= 2) {
                blocks.add(block.toArray(new String[0]));
            }
        }
        return fit(blocks);
    }

    /** One boss relic screen per act and run; Skip is the screen's own pass option, so it stays an opponent and is never reported. */
    public static Map<Integer, Act> computeBoss(List<Run> runs) {
        Map<Integer, List<String[]>> byAct = new TreeMap<Integer, List<String[]>>();
        for (Run r : runs) {
            if (!r.boss.known()) {
                continue;
            }
            for (int i = 0; i < r.boss.value.size(); i++) {
                Run.BossOffer offer = r.boss.value.get(i);
                if (offer == null || offer.picked == null || offer.picked.isEmpty()) {
                    continue;
                }
                List<String> block = new ArrayList<String>();
                block.add(offer.picked);
                for (String id : offer.offered) {
                    if (id != null && !id.isEmpty() && !id.equals(offer.picked)) {
                        block.add(id);
                    }
                }
                if (!block.contains(SKIP)) {
                    block.add(SKIP);
                }
                byAct.computeIfAbsent(Integer.valueOf(i + 1), k -> new ArrayList<String[]>()).add(block.toArray(new String[0]));
            }
        }
        Map<Integer, Act> out = new TreeMap<Integer, Act>();
        for (Map.Entry<Integer, List<String[]>> e : byAct.entrySet()) {
            out.put(e.getKey(), fit(e.getValue()));
        }
        return out;
    }

    /** Fit one act. Blocks list the chosen id first, then the rest of the screen. Public for the offline checks. */
    public static Act fit(List<String[]> blocks) {
        LinkedHashSet<String> universe = new LinkedHashSet<String>();
        if (blocks != null) {
            for (String[] block : blocks) {
                if (block == null || block.length < 2) {
                    continue;
                }
                for (String id : block) {
                    if (id != null && !id.isEmpty()) {
                        universe.add(id);
                    }
                }
            }
        }
        if (universe.size() < 2) {
            return new Act(false, Collections.<String, Double>emptyMap(), 0, universe.size());
        }
        Map<String, Integer> index = new LinkedHashMap<String, Integer>();
        int next = 0;
        for (String id : universe) {
            index.put(id, Integer.valueOf(next++));
        }
        List<int[]> groups = new ArrayList<int[]>();
        for (String[] block : blocks) {
            if (block == null) {
                continue;
            }
            int[] ids = new int[block.length];
            int len = 0;
            for (String id : block) {
                Integer id2 = id == null ? null : index.get(id);
                if (id2 == null) {
                    continue;
                }
                boolean duplicate = false;
                for (int t = 0; t < len; t++) {
                    if (ids[t] == id2.intValue()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    ids[len++] = id2.intValue();
                }
            }
            if (len >= 2) {
                groups.add(len == ids.length ? ids : Arrays.copyOf(ids, len));
            }
        }
        if (groups.size() < 2) {
            return new Act(false, Collections.<String, Double>emptyMap(), groups.size(), universe.size());
        }

        int n = universe.size();
        double[] wins = new double[n];
        boolean[] seen = new boolean[n];
        for (int[] group : groups) {
            wins[group[0]] += 1.0D;
            for (int id : group) {
                seen[id] = true;
            }
        }
        double[] s = new double[n];
        Arrays.fill(s, 1.0D);
        for (int iter = 0; iter < MAX_ITER; iter++) {
            double[] denom = new double[n];
            for (int[] group : groups) {
                double z = 0.0D;
                for (int id : group) {
                    z += s[id];
                }
                if (z <= 0.0D) {
                    continue;
                }
                double part = 1.0D / z;
                for (int id : group) {
                    denom[id] += part;
                }
            }
            double[] updated = new double[n];
            double logSum = 0.0D;
            int active = 0;
            for (int id = 0; id < n; id++) {
                if (!seen[id]) {
                    updated[id] = 0.0D;
                    continue;
                }
                double value = Math.max((wins[id] + PRIOR_WINS) / (denom[id] + PRIOR_SURVIVAL), FLOOR);
                updated[id] = value;
                logSum += Math.log(value);
                active++;
            }
            if (active == 0) {
                break;
            }
            double geo = Math.exp(logSum / active);
            double maxRel = 0.0D;
            for (int id = 0; id < n; id++) {
                if (!seen[id]) {
                    continue;
                }
                double value = Math.max(updated[id] / geo, FLOOR);
                maxRel = Math.max(maxRel, Math.abs(value / s[id] - 1.0D));
                s[id] = value;
            }
            if (maxRel < TOL) {
                break;
            }
        }

        // Reported cards only: SKIP is an opponent, never a row; shares sum to 100 over the cards.
        double total = 0.0D;
        for (Map.Entry<String, Integer> e : index.entrySet()) {
            if (!e.getKey().equals(SKIP) && seen[e.getValue().intValue()]) {
                total += s[e.getValue().intValue()];
            }
        }
        Map<String, Double> share = new LinkedHashMap<String, Double>();
        int cards = 0;
        if (total > 0.0D) {
            for (Map.Entry<String, Integer> e : index.entrySet()) {
                if (e.getKey().equals(SKIP) || !seen[e.getValue().intValue()]) {
                    continue;
                }
                share.put(e.getKey(), Double.valueOf(100.0D * s[e.getValue().intValue()] / total));
                cards++;
            }
        }
        boolean available = cards >= 2;
        return new Act(available, available ? Collections.unmodifiableMap(share) : Collections.<String, Double>emptyMap(), groups.size(), cards);
    }

    private Standardized() {
    }
}
