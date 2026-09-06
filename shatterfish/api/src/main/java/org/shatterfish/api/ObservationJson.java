package org.shatterfish.api;

import java.util.List;
import java.util.Map;

/**
 * The readable form of an Observation (ADR-0005, option 10): one JSON object with the hash, the
 * section hashes and the nine sections, each record an object keyed by its component names, each
 * enum its name, each list an array, each Action an object with its {@code kind} among its
 * components. It is derived from the same records the codec encodes, and {@code CodecReflectionTest}
 * holds that every component reaches it; the hash is the codec's, never a hash of this text, and
 * nothing reads this text back into an Observation ({@code JsonRenderingTest}).
 */
public final class ObservationJson {

    public static final String HASH = "hash";
    public static final String SECTION_HASHES = "sectionHashes";
    public static final String KIND = "kind";

    private ObservationJson() {
    }

    /** The whole Observation, with its hash and section hashes, as canonical JSON. */
    public static String render(Observation observation) {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key(HASH).value(observation.hash());
        out.key(SECTION_HASHES).beginObject();
        for (Map.Entry<String, String> section : observation.sectionHashes().entrySet()) {
            out.key(section.getKey()).value(section.getValue());
        }
        out.endObject();
        out.key(ObservationCodec.HEADER);
        write(out, observation.header());
        out.key(ObservationCodec.MAP);
        write(out, observation.map());
        out.key(ObservationCodec.ACTORS);
        write(out, observation.actors());
        out.key(ObservationCodec.HERO);
        write(out, observation.hero());
        out.key(ObservationCodec.INVENTORY);
        write(out, observation.inventory());
        out.key(ObservationCodec.JOURNAL);
        write(out, observation.journal());
        out.key(ObservationCodec.LOG);
        write(out, observation.log());
        out.key(ObservationCodec.ACTIONS);
        write(out, observation.actions());
        out.key(ObservationCodec.PROMPT);
        write(out, observation.prompt());
        out.endObject();
        return out.toJson();
    }

    /** Any record of the schema on its own, for the test that varies each component. */
    static String renderValue(Object value) {
        if (value instanceof Observation observation) {
            return render(observation);
        }
        JsonWriter out = new JsonWriter();
        if (value instanceof HeaderSection header) {
            write(out, header);
        } else if (value instanceof MapSection map) {
            write(out, map);
        } else if (value instanceof ActorsSection actors) {
            write(out, actors);
        } else if (value instanceof HeroSection hero) {
            write(out, hero);
        } else if (value instanceof InventorySection inventory) {
            write(out, inventory);
        } else if (value instanceof JournalSection journal) {
            write(out, journal);
        } else if (value instanceof LogSection log) {
            write(out, log);
        } else if (value instanceof ActionsSection actions) {
            write(out, actions);
        } else if (value instanceof PromptSection prompt) {
            write(out, prompt);
        } else if (value instanceof TrapView trap) {
            write(out, trap);
        } else if (value instanceof HeapView heap) {
            write(out, heap);
        } else if (value instanceof BlobCell blob) {
            write(out, blob);
        } else if (value instanceof TransitionView transition) {
            write(out, transition);
        } else if (value instanceof ActorView actor) {
            write(out, actor);
        } else if (value instanceof BuffView buff) {
            write(out, buff);
        } else if (value instanceof TalentView talent) {
            write(out, talent);
        } else if (value instanceof QuickslotView quickslot) {
            write(out, quickslot);
        } else if (value instanceof ItemView item) {
            write(out, item);
        } else if (value instanceof NoteView note) {
            write(out, note);
        } else if (value instanceof KnownAppearance known) {
            write(out, known);
        } else if (value instanceof LogLine line) {
            write(out, line);
        } else if (value instanceof ItemRef ref) {
            write(out, ref);
        } else if (value instanceof Action action) {
            write(out, action);
        } else {
            throw new IllegalArgumentException("not a record of the schema: " + value);
        }
        return out.toJson();
    }

    private static void names(JsonWriter out, String key, List<? extends Enum<?>> values) {
        out.key(key).beginArray();
        for (Enum<?> value : values) {
            out.value(value.name());
        }
        out.endArray();
    }

    private static void strings(JsonWriter out, String key, List<String> values) {
        out.key(key).beginArray();
        for (String value : values) {
            out.value(value);
        }
        out.endArray();
    }

    private static void write(JsonWriter out, HeaderSection header) {
        out.beginObject();
        out.key("version").value(header.version());
        out.key("upstreamTag").value(header.upstreamTag());
        out.key("codexVersion").value(header.codexVersion());
        out.key("heroClass").value(header.heroClass().name());
        names(out, "challenges", header.challenges());
        out.key("depth").value(header.depth());
        out.key("branch").value(header.branch());
        out.key("sealed").value(header.sealed());
        out.key("oracle").value(header.oracle());
        out.key("prompt").value(header.prompt().name());
        out.endObject();
    }

    private static void write(JsonWriter out, MapSection map) {
        out.beginObject();
        out.key("width").value(map.width());
        out.key("height").value(map.height());
        names(out, "tiles", map.tiles());
        names(out, "fog", map.fog());
        out.key("traps").beginArray();
        for (TrapView trap : map.traps()) {
            write(out, trap);
        }
        out.endArray();
        out.key("heaps").beginArray();
        for (HeapView heap : map.heaps()) {
            write(out, heap);
        }
        out.endArray();
        out.key("blobs").beginArray();
        for (BlobCell blob : map.blobs()) {
            write(out, blob);
        }
        out.endArray();
        out.key("feeling").value(map.feeling().name());
        out.key("transitions").beginArray();
        for (TransitionView transition : map.transitions()) {
            write(out, transition);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, TrapView trap) {
        out.beginObject();
        out.key("cell").value(trap.cell());
        out.key("kind").value(trap.kind());
        out.key("active").value(trap.active());
        out.endObject();
    }

    private static void write(JsonWriter out, HeapView heap) {
        out.beginObject();
        out.key("cell").value(heap.cell());
        out.key("kind").value(heap.kind().name());
        out.key("hidden").value(heap.hidden());
        out.key("item").value(heap.item());
        out.key("price").value(heap.price());
        out.key("category").value(heap.category());
        out.endObject();
    }

    private static void write(JsonWriter out, BlobCell blob) {
        out.beginObject();
        out.key("cell").value(blob.cell());
        strings(out, "kinds", blob.kinds());
        out.endObject();
    }

    private static void write(JsonWriter out, TransitionView transition) {
        out.beginObject();
        out.key("cell").value(transition.cell());
        out.key("kind").value(transition.kind().name());
        out.endObject();
    }

    private static void write(JsonWriter out, ActorsSection actors) {
        out.beginObject();
        out.key("actors").beginArray();
        for (ActorView actor : actors.actors()) {
            write(out, actor);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, ActorView actor) {
        out.beginObject();
        out.key("cell").value(actor.cell());
        out.key("name").value(actor.name());
        out.key("alignment").value(actor.alignment().name());
        out.key("healthPips").value(actor.healthPips());
        out.key("invisible").value(actor.invisible());
        out.key("emote").value(actor.emote().name());
        buffs(out, actor.buffs());
        out.endObject();
    }

    private static void buffs(JsonWriter out, List<BuffView> buffs) {
        out.key("buffs").beginArray();
        for (BuffView buff : buffs) {
            write(out, buff);
        }
        out.endArray();
    }

    private static void write(JsonWriter out, BuffView buff) {
        out.beginObject();
        out.key("name").value(buff.name());
        out.key("timed").value(buff.timed());
        out.key("turnsHundredths").value(buff.turnsHundredths());
        out.endObject();
    }

    private static void write(JsonWriter out, HeroSection hero) {
        out.beginObject();
        out.key("cell").value(hero.cell());
        out.key("name").value(hero.name());
        out.key("subclass").value(hero.subclass().name());
        out.key("ability").value(hero.ability());
        out.key("level").value(hero.level());
        out.key("exp").value(hero.exp());
        out.key("expToLevel").value(hero.expToLevel());
        out.key("hp").value(hero.hp());
        out.key("ht").value(hero.ht());
        out.key("shield").value(hero.shield());
        out.key("strength").value(hero.strength());
        out.key("strengthBonus").value(hero.strengthBonus());
        out.key("gold").value(hero.gold());
        out.key("energy").value(hero.energy());
        out.key("hunger").value(hero.hunger().name());
        buffs(out, hero.buffs());
        out.key("talents").beginArray();
        for (TalentView talent : hero.talents()) {
            write(out, talent);
        }
        out.endArray();
        out.key("talentPointsAvailable").beginArray();
        for (int points : hero.talentPointsAvailable()) {
            out.value(points);
        }
        out.endArray();
        out.key("quickslots").beginArray();
        for (QuickslotView quickslot : hero.quickslots()) {
            write(out, quickslot);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, TalentView talent) {
        out.beginObject();
        out.key("tier").value(talent.tier());
        out.key("name").value(talent.name());
        out.key("points").value(talent.points());
        out.endObject();
    }

    private static void write(JsonWriter out, QuickslotView quickslot) {
        out.beginObject();
        out.key("item").value(quickslot.item());
        out.key("placeholder").value(quickslot.placeholder());
        out.endObject();
    }

    private static void write(JsonWriter out, InventorySection inventory) {
        out.beginObject();
        out.key("items").beginArray();
        for (ItemView item : inventory.items()) {
            write(out, item);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, ItemView item) {
        out.beginObject();
        out.key("kind").value(item.kind().name());
        out.key("name").value(item.name());
        out.key("quantity").value(item.quantity());
        out.key("levelKnown").value(item.levelKnown());
        out.key("visiblyUpgraded").value(item.visiblyUpgraded());
        out.key("cursedKnown").value(item.cursedKnown());
        out.key("visiblyCursed").value(item.visiblyCursed());
        out.key("status").value(item.status());
        out.key("slot").value(item.slot().name());
        strings(out, "actions", item.actions());
        out.key("defaultAction").value(item.defaultAction());
        out.endObject();
    }

    private static void write(JsonWriter out, JournalSection journal) {
        out.beginObject();
        out.key("notes").beginArray();
        for (NoteView note : journal.notes()) {
            write(out, note);
        }
        out.endArray();
        out.key("known").beginArray();
        for (KnownAppearance known : journal.known()) {
            write(out, known);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, NoteView note) {
        out.beginObject();
        out.key("kind").value(note.kind().name());
        out.key("depth").value(note.depth());
        out.key("title").value(note.title());
        out.key("text").value(note.text());
        out.key("quantity").value(note.quantity());
        out.endObject();
    }

    private static void write(JsonWriter out, KnownAppearance known) {
        out.beginObject();
        out.key("kind").value(known.kind().name());
        out.key("name").value(known.name());
        out.endObject();
    }

    private static void write(JsonWriter out, LogSection log) {
        out.beginObject();
        out.key("lines").beginArray();
        for (LogLine line : log.lines()) {
            write(out, line);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, LogLine line) {
        out.beginObject();
        out.key("tone").value(line.tone().name());
        out.key("text").value(line.text());
        out.endObject();
    }

    private static void write(JsonWriter out, ActionsSection actions) {
        out.beginObject();
        out.key("actions").beginArray();
        for (Action action : actions.actions()) {
            write(out, action);
        }
        out.endArray();
        out.endObject();
    }

    private static void write(JsonWriter out, ItemRef ref) {
        out.beginObject();
        out.key("index").value(ref.index());
        out.key("name").value(ref.name());
        out.key("quantity").value(ref.quantity());
        out.endObject();
    }

    private static void write(JsonWriter out, Action action) {
        out.beginObject();
        out.key(KIND).value(action.kind());
        if (action instanceof Action.Step step) {
            out.key("cell").value(step.cell());
        } else if (action instanceof Action.MoveTo move) {
            out.key("cell").value(move.cell());
        } else if (action instanceof Action.Attack attack) {
            out.key("cell").value(attack.cell());
        } else if (action instanceof Action.Interact interact) {
            out.key("cell").value(interact.cell());
        } else if (action instanceof Action.OpenChest chest) {
            out.key("cell").value(chest.cell());
        } else if (action instanceof Action.Buy buy) {
            out.key("cell").value(buy.cell());
        } else if (action instanceof Action.Unlock unlock) {
            out.key("cell").value(unlock.cell());
        } else if (action instanceof Action.UseItem use) {
            out.key("item");
            write(out, use.item());
            out.key("action").value(use.action());
        } else if (action instanceof Action.UseItemAt use) {
            out.key("item");
            write(out, use.item());
            out.key("action").value(use.action());
            out.key("cell").value(use.cell());
        } else if (action instanceof Action.UseItemOn use) {
            out.key("item");
            write(out, use.item());
            out.key("action").value(use.action());
            out.key("target");
            write(out, use.target());
        } else if (action instanceof Action.UseItemOption use) {
            out.key("item");
            write(out, use.item());
            out.key("action").value(use.action());
            out.key("option").value(use.option());
        } else if (action instanceof Action.Rest rest) {
            out.key("full").value(rest.full());
        } else if (action instanceof Action.Talent talent) {
            out.key("talent").value(talent.talent());
        } else if (action instanceof Action.Ability ability) {
            out.key("ability").value(ability.ability());
        } else if (action instanceof Action.AbilityAt ability) {
            out.key("ability").value(ability.ability());
            out.key("cell").value(ability.cell());
        } else if (action instanceof Action.AnswerPrompt answer) {
            out.key("option").value(answer.option());
        }
        out.endObject();
    }

    private static void write(JsonWriter out, PromptSection prompt) {
        out.beginObject();
        out.key("kind").value(prompt.kind().name());
        out.key("title").value(prompt.title());
        out.key("text").value(prompt.text());
        strings(out, "options", prompt.options());
        out.endObject();
    }
}
