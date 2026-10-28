package org.shatterfish.brain;

import org.shatterfish.api.Codex;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The posterior over what each unidentified appearance in view is, given what the screens showed of the
 * floors (issue #219; docs/rules/generation.md, the potion-room-inference rows).
 *
 * <p>The base model is {@link Beliefs#marginal}: the colours are a uniform shuffle, so an assignment of
 * appearances to distinct identities weighs the product of the identities' deck weights. Two things
 * move it, both from the game's own code:
 *
 * <ul>
 * <li><b>A stack.</b> Held {@code m} of one appearance, an identity of deck weight {@code w} weighs
 * {@code w (w-1) ... (w-m+1)}: the decks hold {@code w} copies between them (Generator.java:342-343), so a stack of
 * four is not a haste potion and is healing far more than the weights alone say.
 * <li><b>A room.</b> A special room that queues a fixed potion puts that potion on its floor
 * (PoolRoom.java:91 and five others). If a potion room was seen on a floor and appearances were first
 * drawn on that floor, one of them is that potion with probability {@code 1 - ROOM_MISS}, whichever it is
 * equally; with {@code ROOM_MISS} it is still unfound (a vault, unexplored ground). Such an appearance weighs
 * as a draw that is not the deck's: the hit's probability over the number of appearances from that floor,
 * against the deck draw's share of the weights (the potion's weight over their sum).
 * </ul>
 *
 * <p>Exact, by dynamic programming over the identities used so far and the rooms satisfied, forward and
 * backward; the states are the subsets of at most the number of appearances in view.
 */
final class Evidence {

    /** The most identities the sparse subset states are kept for; more falls back to the plain weights. */
    private static final int LIMIT = 16;

    /** The most floors' rooms read at once: each doubles the states. */
    private static final int ROOMS = 3;

    /** The largest stack the weights tell apart. */
    private static final int STACK = 6;

    private Evidence() {
    }

    /**
     * Each appearance's odds over {@code names}, as one row per entry of {@code labels}; or null when the plain
     * computation is the same (no stack held, no room fact bearing on these appearances).
     */
    static double[][] marginals(Observation observation, Codex.Identities family, List<String> names,
                                List<Integer> weights, List<String> labels, List<Memory.Fact> facts) {
        int n = names.size();
        int k = labels.size();
        if (n > LIMIT || k > n || k == 0) {
            return null;
        }
        int[] stack = new int[k];
        boolean stacked = false;
        for (int i = 0; i < k; i++) {
            stack[i] = Math.min(STACK, held(observation, labels.get(i)));
            stacked |= stack[i] > 1;
        }
        // The floor each appearance was first drawn on.
        int[] floor = new int[k];
        for (int i = 0; i < k; i++) {
            floor[i] = -1;
            for (Memory.Fact fact : facts) {
                if (fact.because().equals(Beliefs.SEEN) && fact.item().equals(labels.get(i))) {
                    floor[i] = fact.depth();
                    break;
                }
            }
        }
        // The rooms: a queued potion that is still unidentified, on a floor some appearance in view came from.
        List<Integer> target = new ArrayList<>();
        List<Integer> depth = new ArrayList<>();
        List<Integer> from = new ArrayList<>();
        if (family.kind() == org.shatterfish.api.ItemKind.POTION) {
            for (Memory.Fact fact : facts) {
                int x = names.indexOf(fact.item());
                if (fact.because().equals(Beliefs.SEEN) || x < 0) {
                    continue;
                }
                int count = 0;
                for (int f : floor) {
                    if (f == fact.depth()) {
                        count++;
                    }
                }
                boolean already = false;
                for (int r = 0; r < target.size(); r++) {
                    already |= target.get(r) == x && depth.get(r) == fact.depth();
                }
                if (count > 0 && !already) {
                    target.add(x);
                    depth.add(fact.depth());
                    from.add(count);
                }
            }
            while (target.size() > ROOMS) {
                target.remove(0);
                depth.remove(0);
                from.remove(0);
            }
        }
        if (!stacked && target.isEmpty()) {
            return null;
        }
        int rooms = target.size();
        double total = 0;
        for (int w : weights) {
            total += w;
        }
        // factor[i][x]: the weight of appearance i being identity x; hit[i][x]: the rooms that makes satisfied.
        double[][] factor = new double[k][n];
        int[][] hit = new int[k][n];
        for (int i = 0; i < k; i++) {
            for (int x = 0; x < n; x++) {
                double w = weights.get(x);
                double product = 1;
                // Only the decks' copies run out; what the floors queue (strength, upgrade) has no count to exhaust.
                int copies = 1;
                for (Codex.Candidate candidate : family.candidates()) {
                    if (candidate.name().equals(names.get(x)) && candidate.weight() > 0) {
                        copies = stack[i];
                    }
                }
                for (int j = 0; j < copies; j++) {
                    product *= Math.max(0, w - j);
                }
                for (int r = 0; r < rooms; r++) {
                    if (target.get(r) == x && depth.get(r) == floor[i]) {
                        hit[i][x] |= 1 << r;
                        // Not a deck draw: the queued potion, one of the appearances from its floor.
                        product = product / w * total * (1 - Beliefs.ROOM_MISS) / from.get(r);
                    }
                }
                factor[i][x] = product;
            }
        }
        List<Map<Long, Double>> forward = new ArrayList<>();
        Map<Long, Double> start = new HashMap<>();
        start.put(0L, 1.0);
        forward.add(start);
        for (int i = 0; i < k; i++) {
            Map<Long, Double> next = new HashMap<>();
            for (Map.Entry<Long, Double> state : forward.get(i).entrySet()) {
                for (int x = 0; x < n; x++) {
                    if (used(state.getKey(), x) || factor[i][x] == 0) {
                        continue;
                    }
                    next.merge(step(state.getKey(), x, hit[i][x]), state.getValue() * factor[i][x], Double::sum);
                }
            }
            forward.add(next);
        }
        List<Map<Long, Double>> backward = new ArrayList<>();
        for (int i = 0; i <= k; i++) {
            backward.add(new HashMap<>());
        }
        for (long state : forward.get(k).keySet()) {
            double done = 1;
            for (int r = 0; r < rooms; r++) {
                if ((state & (1L << r)) == 0) {
                    done *= Beliefs.ROOM_MISS;
                }
            }
            backward.get(k).put(state, done);
        }
        for (int i = k - 1; i >= 0; i--) {
            for (long state : forward.get(i).keySet()) {
                double sum = 0;
                for (int x = 0; x < n; x++) {
                    if (used(state, x) || factor[i][x] == 0) {
                        continue;
                    }
                    Double rest = backward.get(i + 1).get(step(state, x, hit[i][x]));
                    if (rest != null) {
                        sum += factor[i][x] * rest;
                    }
                }
                backward.get(i).put(state, sum);
            }
        }
        double[][] marginal = new double[k][n];
        for (int i = 0; i < k; i++) {
            double sum = 0;
            for (Map.Entry<Long, Double> state : forward.get(i).entrySet()) {
                for (int x = 0; x < n; x++) {
                    if (used(state.getKey(), x) || factor[i][x] == 0) {
                        continue;
                    }
                    Double rest = backward.get(i + 1).get(step(state.getKey(), x, hit[i][x]));
                    if (rest != null) {
                        marginal[i][x] += state.getValue() * factor[i][x] * rest;
                    }
                }
            }
            for (int x = 0; x < n; x++) {
                sum += marginal[i][x];
            }
            if (sum <= 0) {
                return null;
            }
            for (int x = 0; x < n; x++) {
                marginal[i][x] /= sum;
            }
        }
        return marginal;
    }

    /** States hold the rooms satisfied in the low bits and the identities used above them. */
    private static final int SHIFT = 8;

    private static boolean used(long state, int identity) {
        return (state >> (SHIFT + identity) & 1L) != 0;
    }

    private static long step(long state, int identity, int satisfied) {
        return state | (1L << (SHIFT + identity)) | satisfied;
    }

    /** How many of an appearance the pack and the heaps in view hold together. */
    static int held(Observation observation, String label) {
        int quantity = 0;
        for (ItemView item : observation.inventory().items()) {
            if (item.name().equals(label)) {
                quantity += item.quantity();
            }
        }
        for (HeapView heap : observation.map().heaps()) {
            if (!heap.item().isEmpty() && Beliefs.untitled(heap.item()).equals(label)) {
                quantity += heapQuantity(heap.item());
            }
        }
        return Math.max(1, quantity);
    }

    /** The count a heap's title ends with ("crimson potion x2"), or one. */
    static int heapQuantity(String title) {
        int space = title.lastIndexOf(" x");
        if (space > 0) {
            String tail = title.substring(space + 2);
            if (!tail.isEmpty() && tail.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return Integer.parseInt(tail);
            }
        }
        return 1;
    }
}
