package org.shatterfish.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numbers a fight search runs by (story 10.24): how many determinizations, how far to roll each out, which Actions
 * to try, when to look at all, and what a rollout is worth. They are data a caller hands the Brain, as a Playbook is,
 * and their canonical text joins the Brain's configuration, so two plans are two configurations and a Run log says
 * which one was played.
 *
 * @param samples the determinizations of the hidden state each candidate is rolled out under (K)
 * @param horizon the Actions each rollout plays, the candidate first (H)
 * @param candidates the most distinct Actions tried
 * @param radius how near, in cells, an awake enemy that walks must be for the search to look (an enemy beside the hero always qualifies)
 * @param marginMilli how much better than the rules' own pick, in thousandths of the hero's maximum hit points, a
 *                    candidate's mean must be for the search to deviate from it
 * @param deathMilli what the hero's death costs, in the same unit
 * @param enemyMilli what taking an enemy from full health to defeated is worth, in the same unit
 * @param gate 0 looks at every engaged fight with a choice in it; 1 only at the dangerous ones (an unfavourable fight, a
 *             hero under three quarters of its health, or two enemies or more in view)
 */
public record SearchPlan(int samples, int horizon, int candidates, int radius, int marginMilli, int deathMilli,
                         int enemyMilli, int gate) {

    /** The plan the Rig plays unless it is told another. */
    public static final SearchPlan REFERENCE = new SearchPlan(4, 8, 3, 3, 100, 3000, 1000, 1);

    public SearchPlan {
        Canon.require(samples >= 1 && samples <= 64, "a search takes 1 to 64 samples: " + samples);
        Canon.require(horizon >= 1 && horizon <= 64, "a rollout plays 1 to 64 Actions: " + horizon);
        Canon.require(candidates >= 2 && candidates <= 8, "a search weighs 2 to 8 candidates: " + candidates);
        Canon.require(radius >= 1 && radius <= 12, "a search looks at enemies 1 to 12 cells away: " + radius);
        Canon.require(marginMilli >= 0 && deathMilli >= 0 && enemyMilli >= 0, "a plan's values are not negative");
        Canon.require(gate == 0 || gate == 1, "the gate is 0 or 1: " + gate);
    }

    /** The plan as text, in a fixed order: what the configuration hashes. */
    public String canonical() {
        return "k=" + samples + ",h=" + horizon + ",c=" + candidates + ",r=" + radius + ",m=" + marginMilli + ",d=" + deathMilli
                + ",e=" + enemyMilli + ",g=" + gate;
    }

    /** The plan whose canonical text, or any part of it (the rest taken from the reference plan), is {@code text}. */
    public static SearchPlan parse(String text) {
        Map<String, Integer> held = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.strip().split(",")) {
                String[] pair = part.strip().split("=");
                Canon.require(pair.length == 2, "a search plan part is key=value: " + part);
                try {
                    Canon.require(held.put(pair[0], Integer.parseInt(pair[1])) == null, "a search plan key is given once: " + pair[0]);
                } catch (NumberFormatException notANumber) {
                    throw new IllegalArgumentException("a search plan value is a whole number: " + part, notANumber);
                }
            }
        }
        SearchPlan r = REFERENCE;
        SearchPlan plan = new SearchPlan(take(held, "k", r.samples), take(held, "h", r.horizon), take(held, "c", r.candidates),
                take(held, "r", r.radius), take(held, "m", r.marginMilli), take(held, "d", r.deathMilli),
                take(held, "e", r.enemyMilli), take(held, "g", r.gate));
        Canon.require(held.isEmpty(), "a search plan does not know " + held.keySet());
        return plan;
    }

    private static int take(Map<String, Integer> held, String key, int fallback) {
        Integer value = held.remove(key);
        return value == null ? fallback : value;
    }
}
