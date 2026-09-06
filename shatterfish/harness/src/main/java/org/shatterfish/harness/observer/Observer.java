package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.QuickSlot;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.FlavourBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.EbonyMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GoldenMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.effects.EmoIcon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.KnownAppearance;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.NoteView;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.TalentView;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Prompts;
import org.shatterfish.harness.driver.Windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one door from game state to the bot (non-negotiable 1; ADR-0006): reads, at an Input wait,
 * exactly what the screen draws, through the predicates the renderer and the HUD use, and builds
 * the sections of the Observation from them. Story 1.8 built the header and the map, story 1.9
 * the actors and the hero, story 1.10 the inventory, the journal, the log and the Prompt; the
 * rows left (1.11) follow, and {@code observe()} arrives when every section does. Nothing here reads a field the
 * screen does not draw, and every rule cites the drawing code at the pinned tag; paths abbreviate
 * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}.
 *
 * <p>Every method runs only at an Input wait: the hero is ready with no action and not resting,
 * and either no window is open or the window in front is a Prompt, one the game opened on its
 * own and waits on ({@link Prompts}), which is what the driver confirms (ADR-0015) and what this
 * class asserts on entry; any other window in front is a failure (ADR-0006, Prompt). The log is
 * read from {@link GameLogListener}, the listener on the game's message signal that hook row 3
 * re-registers on every scene creation.
 */
public final class Observer {

    /**
     * What each visual of the tile sheet's two tables looks like, as a {@link Tile}. The sheet
     * draws several terrains with one visual ({@code …/tiles/DungeonTileSheet.java:427-431},
     * {@code :446-447}, {@code :464}), so the table is keyed by visual and a terrain reaches it
     * through the sheet's own tables, never through a table of Shatterfish's own.
     */
    private static final Map<Integer, Tile> BY_VISUAL = new HashMap<>();

    static {
        visual(DungeonTileSheet.FLOOR, Tile.EMPTY);
        visual(DungeonTileSheet.GRASS, Tile.GRASS);
        visual(DungeonTileSheet.EMPTY_WELL, Tile.EMPTY_WELL);
        visual(DungeonTileSheet.ENTRANCE, Tile.ENTRANCE);
        visual(DungeonTileSheet.EXIT, Tile.EXIT);
        visual(DungeonTileSheet.EMBERS, Tile.EMBERS);
        visual(DungeonTileSheet.PEDESTAL, Tile.PEDESTAL);
        visual(DungeonTileSheet.FLOOR_SP, Tile.EMPTY_SP);
        visual(DungeonTileSheet.ENTRANCE_SP, Tile.ENTRANCE_SP);
        visual(DungeonTileSheet.FLOOR_DECO, Tile.EMPTY_DECO);
        visual(DungeonTileSheet.LOCKED_EXIT, Tile.LOCKED_EXIT);
        visual(DungeonTileSheet.UNLOCKED_EXIT, Tile.UNLOCKED_EXIT);
        visual(DungeonTileSheet.WELL, Tile.WELL);
        visual(DungeonTileSheet.FLAT_WALL, Tile.WALL);
        visual(DungeonTileSheet.FLAT_DOOR, Tile.DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_OPEN, Tile.OPEN_DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_LOCKED, Tile.LOCKED_DOOR);
        visual(DungeonTileSheet.FLAT_DOOR_CRYSTAL, Tile.CRYSTAL_DOOR);
        visual(DungeonTileSheet.FLAT_WALL_DECO, Tile.WALL_DECO);
        visual(DungeonTileSheet.FLAT_BOOKSHELF, Tile.BOOKSHELF);
        visual(DungeonTileSheet.FLAT_ALCHEMY_POT, Tile.ALCHEMY);
        visual(DungeonTileSheet.FLAT_BARRICADE, Tile.BARRICADE);
        visual(DungeonTileSheet.FLAT_HIGH_GRASS, Tile.HIGH_GRASS);
        visual(DungeonTileSheet.FLAT_FURROWED_GRASS, Tile.FURROWED_GRASS);
        visual(DungeonTileSheet.FLAT_STATUE, Tile.STATUE);
        visual(DungeonTileSheet.FLAT_STATUE_SP, Tile.STATUE_SP);
        visual(DungeonTileSheet.FLAT_REGION_DECO, Tile.REGION_DECO);
        visual(DungeonTileSheet.FLAT_REGION_DECO_ALT, Tile.REGION_DECO_ALT);
    }

    private static void visual(int visual, Tile tile) {
        Tile before = BY_VISUAL.put(visual, tile);
        if (before != null) {
            throw new IllegalStateException("the tile sheet draws " + before + " and " + tile + " with one visual, " + visual
                    + "; the map from visuals to tiles is not a function any more");
        }
    }

    public Observer() {
    }

    /** The upstream release the game is: the version the launcher set, as the tag names it. */
    public static String upstreamTag() {
        return "v" + Game.version;
    }

    /**
     * The header (ADR-0005): the schema version, the release, the hero's class, the challenges
     * the Run was started with ({@code …/Challenges.java:43-64}; the challenges window and the
     * hero window both show them), the depth and branch the interlevel screen and the status pane
     * name, whether the floor is locked by a boss fight ({@code …/levels/Level.java:180}, set by
     * {@code seal()} with the {@code LockedFloor} buff whose icon the HUD shows, {@code :617-630};
     * {@code …/actors/buffs/LockedFloor.java:76-78}), no oracle, and the kind of the Prompt in front,
     * {@code NONE} with no window ({@link Prompts#kind}), which the prompt section repeats.
     */
    public HeaderSection header() {
        atInputWait();
        Hero hero = Dungeon.hero;
        List<Challenge> challenges = new ArrayList<>();
        for (int i = 0; i < Challenges.MASKS.length; i++) {
            if ((Dungeon.challenges & Challenges.MASKS[i]) != 0) {
                challenges.add(Challenge.valueOf(Challenges.NAME_IDS[i].toUpperCase(Locale.ROOT)));
            }
        }
        return new HeaderSection(ObservationCodec.SCHEMA_VERSION, upstreamTag(), "", HeroClass.valueOf(hero.heroClass.name()),
                challenges, Dungeon.depth, Dungeon.branch, Dungeon.level.locked, false, Prompts.kind(Windows.front()));
    }

    /**
     * The map (ADR-0005; ADR-0006, Cell visibility, Terrain, Traps, Heaps): per cell the fog
     * level the fog of war paints and, where the fog is not opaque, the tile the terrain tilemap
     * draws; the traps whose feature tile is drawn on a cell the fog does not hide; the heaps whose
     * sprite is visible on such a cell, showing what the sprite and the heap's own title show; and
     * a hidden mimic as the chest it is drawn as, which is a heap here and never an actor
     * ({@code …/actors/mobs/Mimic.java:62-64}, {@code :112-118}). Blobs, the floor feeling and
     * the transitions are story 1.11's and are empty here.
     */
    public MapSection map() {
        atInputWait();
        Level level = Dungeon.level;
        int cells = level.length();
        List<Fog> fog = new ArrayList<>(cells);
        List<Tile> tiles = new ArrayList<>(cells);
        for (int cell = 0; cell < cells; cell++) {
            Fog f = fog(level, cell);
            fog.add(f);
            tiles.add(f == Fog.UNKNOWN ? Tile.NONE : tile(level.map[cell]));
        }
        List<TrapView> traps = new ArrayList<>();
        for (Trap trap : level.traps.valueList()) {
            // The feature layer draws a trap only while it is visible, in its colour while active
            // and black once disarmed (…/tiles/TerrainFeaturesTilemap.java:56-62); the fog of war
            // then paints an unknown cell opaque over it (…/tiles/FogOfWar.java:200-205).
            if (trap.visible && fog.get(trap.pos) != Fog.UNKNOWN) {
                traps.add(new TrapView(trap.pos, trap.name(), trap.active));
            }
        }
        Map<Integer, HeapView> heaps = new LinkedHashMap<>();
        for (Heap heap : level.heaps.valueList()) {
            // The sprite is visible once the heap has been seen and stays so (…/sprites/ItemSprite.java:323-326;
            // …/levels/Level.java:991), blank for an empty heap (:213-215), faint for a hidden one
            // (:236), and it shows the top item or the container (:216-231); the heap's title
            // prints a single for-sale item's price (…/items/Heap.java:368-376) and its
            // description names a crystal chest's category (:394-406).
            if (!heap.seen || fog.get(heap.pos) == Fog.UNKNOWN || heap.items == null || heap.size() <= 0) {
                continue;
            }
            HeapKind kind = HeapKind.valueOf(heap.type.name());
            Item top = heap.peek();
            String item = kind == HeapKind.HEAP || kind == HeapKind.FOR_SALE ? top.title() : "";
            int price = kind == HeapKind.FOR_SALE && heap.size() == 1 ? Shopkeeper.sellPrice(top) : 0;
            String category = kind == HeapKind.CRYSTAL_CHEST ? category(top) : "";
            heaps.put(heap.pos, new HeapView(heap.pos, kind, heap.hidden, item, price, category));
        }
        for (Mob mob : level.mobs) {
            if (!(mob instanceof Mimic mimic) || !hiddenMimic(mimic)) {
                continue;
            }
            // A hidden mimic's sprite is the chest's (…/sprites/MimicSprite.java), drawn like any
            // mob's in view, and once its cell is visited when the mimic is stealthy
            // (…/scenes/GameScene.java:1441-1447); it names itself as the chest and describes a
            // crystal chest's category as the chest would (…/actors/mobs/CrystalMimic.java:68-84).
            boolean drawn = (mimic.stealthy() && level.visited[mimic.pos]) || level.heroFOV[mimic.pos];
            if (!drawn || fog.get(mimic.pos) == Fog.UNKNOWN) {
                continue;
            }
            String category = mimic instanceof CrystalMimic ? mimicCategory(mimic) : "";
            // An ebony mimic hides at alpha 0.2 (…/sprites/MimicSprite.java:121-125): drawn faint, as
            // a hidden heap is, so it carries the flag a faint heap carries.
            boolean faint = mimic instanceof EbonyMimic;
            heaps.put(mimic.pos, new HeapView(mimic.pos, mimicKind(mimic), faint, "", 0, category));
        }
        return new MapSection(level.width(), level.height(), tiles, fog, traps, new ArrayList<>(heaps.values()), List.of(),
                Feeling.NONE, List.of());
    }

    /**
     * The actors (ADR-0005; ADR-0006, Mobs, Mob state, Mob buffs): every character but the hero
     * whose sprite is drawn, which is every mob in the hero's field of view
     * ({@code …/scenes/GameScene.java:1447}; {@code …/actors/Char.java:1272-1274}), except a
     * hidden mimic, which is a heap of the map. Each carries its display name, its alignment, its
     * health as the bar over it draws it, whether it is drawn faint for invisibility
     * ({@code …/sprites/CharSprite.java:401-407}), the emote its sprite shows, and every buff with
     * an icon, as the examine window's row lists them ({@code …/windows/WndInfoMob.java:63-64},
     * {@code :80}).
     */
    public ActorsSection actors() {
        atInputWait();
        Level level = Dungeon.level;
        List<ActorView> actors = new ArrayList<>();
        for (Mob mob : level.mobs) {
            if (hiddenMimic(mob) || !level.heroFOV[mob.pos]) {
                continue;
            }
            actors.add(new ActorView(mob.pos, mob.name(), Alignment.valueOf(mob.alignment.name()), healthPips(mob),
                    mob.invisible > 0, emote(mob), buffs(mob)));
        }
        return new ActorsSection(actors);
    }

    /**
     * The hero (ADR-0005; ADR-0006, Hero buffs) as the status pane, the hero window, the talents
     * pane, the bag window and the quickslots show it; the cites are on {@link HeroSection}. The
     * talents are those of the tiers the pane shows, which it counts from the level with the
     * subclass and the ability as gates ({@code …/ui/TalentsPane.java:75-86}); the hunger is the
     * icon's state ({@code …/actors/buffs/Hunger.java:179-187}); a quickslot's placeholder is an
     * item of no quantity ({@code …/QuickSlot.java:72-74}).
     */
    public HeroSection hero() {
        atInputWait();
        Hero hero = Dungeon.hero;
        require(Talent.MAX_TALENT_TIERS == HeroSection.TALENT_TIERS, "the talent tiers of the tag are the schema's");
        require(QuickSlot.SIZE == HeroSection.QUICKSLOTS, "the quickslots of the tag are the schema's");
        int tiers = 1;
        while (tiers < Talent.MAX_TALENT_TIERS && hero.lvl + 1 >= Talent.tierLevelThresholds[tiers + 1]) {
            tiers++;
        }
        if (tiers > 2 && hero.subClass == HeroSubClass.NONE) {
            tiers = 2;
        } else if (tiers > 3 && hero.armorAbility == null) {
            tiers = 3;
        }
        tiers = Math.min(tiers, hero.talents.size());
        List<TalentView> talents = new ArrayList<>();
        for (int tier = 1; tier <= tiers; tier++) {
            for (Map.Entry<Talent, Integer> entry : hero.talents.get(tier - 1).entrySet()) {
                talents.add(new TalentView(tier, entry.getKey().title(), entry.getValue()));
            }
        }
        List<Integer> available = new ArrayList<>();
        for (int tier = 1; tier <= HeroSection.TALENT_TIERS; tier++) {
            available.add(Math.max(0, hero.talentPointsAvailable(tier)));
        }
        List<QuickslotView> quickslots = new ArrayList<>();
        for (int slot = 0; slot < QuickSlot.SIZE; slot++) {
            Item item = Dungeon.quickslot.getItem(slot);
            quickslots.add(new QuickslotView(item == null ? "" : item.name(), Dungeon.quickslot.isPlaceholder(slot)));
        }
        return new HeroSection(hero.pos, hero.name(), HeroSubclass.valueOf(hero.subClass.name()),
                hero.armorAbility == null ? "" : hero.armorAbility.name(), hero.lvl, hero.exp, hero.maxExp(), hero.HP, hero.HT,
                hero.shielding(), hero.STR, hero.STR() - hero.STR, Dungeon.gold, Dungeon.energy, hunger(hero), buffs(hero),
                talents, available, quickslots);
    }

    /**
     * The inventory (ADR-0005; ADR-0006, Items): the belongings in the order the bag iterates them,
     * the six slots then the backpack with a bag before its contents
     * ({@code …/actors/hero/Belongings.java:422-453}; {@code …/items/bags/Bag.java:216-250}), each
     * item as the slot, the item window and the log print it. The name is the item's own, which for
     * an unknown potion, scroll or ring is its appearance ({@code …/items/potions/Potion.java:377-379};
     * {@code …/items/scrolls/Scroll.java:240-242}; {@code …/items/rings/Ring.java:172-174}) and for a
     * weapon or armor names a curse only once the curse is known
     * ({@code …/items/weapon/Weapon.java:408-416}; {@code …/items/armor/Armor.java:573-581}); the
     * level and curse flags carry the values the slot draws ({@code …/items/Item.java:433-443};
     * {@code …/ui/ItemSlot.java:271-275}); the status is the slot's text, a wand's charges only
     * once known ({@code …/items/wands/Wand.java:336-343}; {@code ItemSlot.java:234}); the actions
     * are the item window's buttons with the default it colours
     * ({@code Item.java:110-115}, {@code :179-181}; {@code …/windows/WndUseItem.java:54-76}), as
     * the identifiers the window executes. The family is the item's package ({@link ItemKind}).
     */
    public InventorySection inventory() {
        atInputWait();
        Hero hero = Dungeon.hero;
        List<ItemView> items = new ArrayList<>();
        for (Item item : hero.belongings) {
            items.add(itemView(item, hero, slot(item, hero.belongings)));
        }
        return new InventorySection(items);
    }

    /**
     * The journal (ADR-0005; ADR-0006, Journal, Known appearances): every note the notes tab draws,
     * the written notes and then each floor's landmarks and keys down from the deepest
     * ({@code …/windows/WndJournal.java:497-541}; {@code …/journal/Notes.java:685-693}), as its kind,
     * depth, title, the body a written note has and the count a key has
     * ({@code Notes.java:206-217}, {@code :324-331}, {@code :344-346}, {@code :430-437}, {@code :487-495});
     * and the potions, scrolls and rings identified this Run
     * ({@code …/items/potions/Potion.java:402-404}; {@code …/items/scrolls/Scroll.java:265-267};
     * {@code …/items/rings/Ring.java:280-282}), each by the name it draws once known
     * ({@code …/items/Item.java:501-503}).
     */
    public JournalSection journal() {
        atInputWait();
        List<NoteView> notes = new ArrayList<>();
        for (Notes.Record record : Notes.getRecords(Notes.Record.class)) {
            notes.add(note(record));
        }
        List<KnownAppearance> known = new ArrayList<>();
        for (Class<? extends Potion> potion : Potion.getKnown()) {
            known.add(new KnownAppearance(ItemKind.POTION, Messages.get(potion, "name")));
        }
        for (Class<? extends Scroll> scroll : Scroll.getKnown()) {
            known.add(new KnownAppearance(ItemKind.SCROLL, Messages.get(scroll, "name")));
        }
        for (Class<? extends Ring> ring : Ring.getKnown()) {
            known.add(new KnownAppearance(ItemKind.RING, Messages.get(ring, "name")));
        }
        return new JournalSection(notes, known);
    }

    /**
     * The log (ADR-0005; ADR-0006, Log): the newest messages of the game's own signal, as
     * {@link GameLogListener} keeps them, never the pane's rendered entries.
     */
    public LogSection log() {
        atInputWait();
        return new LogSection(GameLogListener.INSTANCE.lines());
    }

    /**
     * The Prompt (ADR-0005; ADR-0006, Prompt): the window in front when there is one, which the
     * gate holds to be a Prompt, as its kind ({@link Prompts#kind}), the title and the text it draws
     * and its buttons' labels in drawing order ({@link Windows}); {@link PromptSection#NONE} with no
     * window in front.
     */
    public PromptSection prompt() {
        atInputWait();
        Window window = Windows.front();
        return window == null ? PromptSection.NONE : promptOf(window);
    }

    /**
     * A window's title, text and options. Every Prompt window at the tag draws a title first, an
     * icon title's label or a title block, then one message block, then its buttons
     * ({@code …/windows/WndOptions.java:40-66}; {@code …/windows/WndTitledMessage.java:42-54};
     * {@code …/windows/WndResurrect.java:65-74}; {@code …/windows/WndChooseSubclass.java:49-93});
     * an options window opened without a title draws the message alone ({@code WndOptions.java:53-65}).
     * So the title is the first text block when there are at least two, the text is the rest, and
     * the options are the styled buttons' labels; the icon buttons beside them are not options.
     */
    static PromptSection promptOf(Window window) {
        List<String> texts = Windows.texts(window);
        String title = texts.size() >= 2 ? texts.get(0) : "";
        List<String> rest = texts.size() >= 2 ? texts.subList(1, texts.size()) : texts;
        return new PromptSection(Prompts.kind(window), title, String.join("\n", rest), Windows.buttons(window));
    }

    /**
     * One item as the bag and the item window show it. The status is null for most items
     * ({@code …/items/Item.java:570-572}), an empty string here; the actions are the identifiers
     * {@code actions(hero)} lists, one button each ({@code …/windows/WndUseItem.java:54-76}).
     */
    static ItemView itemView(Item item, Hero hero, EquipSlot slot) {
        String status = item.status();
        String defaultAction = item.defaultAction();
        return new ItemView(itemKind(item), item.name(), item.quantity(), item.levelKnown, item.visiblyUpgraded(),
                item.cursedKnown, item.visiblyCursed(), status == null ? "" : status, slot,
                new ArrayList<>(item.actions(hero)), defaultAction == null ? "" : defaultAction);
    }

    /** The slot an item is worn in, by identity with the belongings' six fields ({@code …/actors/hero/Belongings.java:82-95}). */
    static EquipSlot slot(Item item, Belongings belongings) {
        if (item == belongings.weapon) {
            return EquipSlot.WEAPON;
        } else if (item == belongings.armor) {
            return EquipSlot.ARMOR;
        } else if (item == belongings.artifact) {
            return EquipSlot.ARTIFACT;
        } else if (item == belongings.misc) {
            return EquipSlot.MISC;
        } else if (item == belongings.ring) {
            return EquipSlot.RING;
        } else if (item == belongings.secondWep) {
            return EquipSlot.SECOND_WEAPON;
        }
        return EquipSlot.NONE;
    }

    /**
     * The family the item's package places it in, one member per package of {@code …/items/} at the
     * tag as {@link ItemKind} lays them out, the plants' seeds, and {@code OTHER} for the root, the
     * guide pages and the remains. A nested or anonymous class shares its package.
     */
    static ItemKind itemKind(Item item) {
        String base = "com.shatteredpixel.shatteredpixeldungeon.";
        String pkg = item.getClass().getPackageName();
        if (!pkg.startsWith(base)) {
            return ItemKind.OTHER;
        }
        String rel = pkg.substring(base.length());
        if (rel.equals("plants")) {
            return ItemKind.SEED;
        }
        if (!rel.startsWith("items")) {
            return ItemKind.OTHER;
        }
        String family = rel.equals("items") ? "" : rel.substring("items.".length());
        if (family.startsWith("weapon.missiles")) {
            return ItemKind.MISSILE;
        } else if (family.startsWith("weapon")) {
            return ItemKind.WEAPON;
        } else if (family.startsWith("armor")) {
            return ItemKind.ARMOR;
        } else if (family.startsWith("wands")) {
            return ItemKind.WAND;
        } else if (family.startsWith("rings")) {
            return ItemKind.RING;
        } else if (family.startsWith("artifacts")) {
            return ItemKind.ARTIFACT;
        } else if (family.startsWith("trinkets")) {
            return ItemKind.TRINKET;
        } else if (family.startsWith("potions")) {
            return ItemKind.POTION;
        } else if (family.startsWith("scrolls")) {
            return ItemKind.SCROLL;
        } else if (family.startsWith("stones")) {
            return ItemKind.STONE;
        } else if (family.startsWith("spells")) {
            return ItemKind.SPELL;
        } else if (family.startsWith("bombs")) {
            return ItemKind.BOMB;
        } else if (family.startsWith("food")) {
            return ItemKind.FOOD;
        } else if (family.startsWith("keys")) {
            return ItemKind.KEY;
        } else if (family.startsWith("bags")) {
            return ItemKind.BAG;
        } else if (family.startsWith("quest")) {
            return ItemKind.QUEST;
        }
        return ItemKind.OTHER;
    }

    /**
     * A note as the tab draws it: a key's title and count, a written note's title, body and the depth
     * it names, a landmark's title at its floor ({@code …/journal/Notes.java:206-217}, {@code :324-331},
     * {@code :430-437}, {@code :487-495}).
     */
    static NoteView note(Notes.Record record) {
        if (record instanceof Notes.KeyRecord key) {
            return new NoteView(NoteKind.KEY, key.depth(), text(key.title()), "", key.quantity());
        } else if (record instanceof Notes.CustomRecord custom) {
            return new NoteView(NoteKind.CUSTOM, custom.depth(), text(custom.title()), text(custom.desc()), 1);
        }
        return new NoteView(NoteKind.LANDMARK, record.depth(), text(record.title()), "", 1);
    }

    private static String text(String drawn) {
        return drawn == null ? "" : drawn;
    }

    /** Whether a mob is a mimic still hiding as a chest ({@code …/actors/mobs/Mimic.java:62-64}, {@code :112-118}). */
    static boolean hiddenMimic(Mob mob) {
        return mob instanceof Mimic && mob.alignment == Char.Alignment.NEUTRAL && mob.state == mob.PASSIVE;
    }

    /** The chest a hidden mimic is drawn as ({@code …/sprites/MimicSprite.java:101-125}). */
    static HeapKind mimicKind(Mimic mimic) {
        if (mimic instanceof GoldenMimic) {
            return HeapKind.LOCKED_CHEST;
        } else if (mimic instanceof CrystalMimic) {
            return HeapKind.CRYSTAL_CHEST;
        } else if (mimic instanceof EbonyMimic) {
            return HeapKind.EBONY_CHEST;
        } else {
            return HeapKind.CHEST;
        }
    }

    /** The category a hidden crystal mimic's description names ({@code …/actors/mobs/CrystalMimic.java:68-84}). */
    private static String mimicCategory(Mimic mimic) {
        if (mimic.items != null) {
            for (Item item : mimic.items) {
                if (item instanceof Artifact) {
                    return Messages.get(Heap.class, "artifact");
                } else if (item instanceof Ring) {
                    return Messages.get(Heap.class, "ring");
                } else if (item instanceof Wand) {
                    return Messages.get(Heap.class, "wand");
                }
            }
        }
        return "";
    }

    /**
     * A character's health as the bar over its sprite draws it: the lit share of a bar that is full
     * at the greater of the maximum health and the health plus shielding
     * ({@code …/ui/HealthBar.java:82-88}), in the codec's pips. The bar is hidden at full health
     * with no shield ({@code …/ui/CharHealthIndicator.java:55}), which reads as every pip lit.
     */
    static int healthPips(Char ch) {
        int max = Math.max(ch.HP + ch.shielding(), ch.HT);
        return ObservationCodec.healthPips(Math.max(0, Math.min(ch.HP, max)), max);
    }

    /**
     * The emote the mob's sprite shows at the frame a human decides on. The sleep icon is the
     * sprite's own per-frame function of the mob's state: every update shows it while the mob is
     * alive and sleeping and hides it otherwise, replacing any other icon
     * ({@code …/sprites/MobSprite.java:39}; {@code …/sprites/CharSprite.java:635-639}, {@code :655-675}).
     * The driver's frame updates the sprites before the acts of a turn, so at an Input wait the
     * sprite still carries the icon of the frame before those acts; the rule is applied here as
     * the next frame applies it, from the one bit of state the renderer reads for it, and a sleep
     * icon that frame would hide is dropped. The alert, investigate and lost icons are set and
     * cleared by the acts themselves ({@code …/actors/mobs/Mob.java:229-238}) and are read from
     * the sprite through the accessor of hook row 4 ({@code …/effects/EmoIcon.java:102},
     * {@code :126}, {@code :150}).
     */
    static Emote emote(Mob mob) {
        if (mob.isAlive() && mob.state == mob.SLEEPING) {
            return Emote.SLEEP;
        }
        EmoIcon emo = mob.sprite == null ? null : mob.sprite.shatterfishEmote();
        if (emo == null || !emo.alive || emo instanceof EmoIcon.Sleep) {
            return Emote.NONE;
        } else if (emo instanceof EmoIcon.Alert) {
            return Emote.ALERT;
        } else if (emo instanceof EmoIcon.Investigate) {
            return Emote.INVESTIGATE;
        } else if (emo instanceof EmoIcon.Lost) {
            return Emote.LOST;
        } else {
            return Emote.NONE;
        }
    }

    /**
     * Every buff with an icon, as the buff indicators list them ({@code …/ui/BuffIndicator.java:192-196};
     * {@code …/windows/WndHero.java:301-314}; {@code …/windows/WndInfoMob.java:63-64}), with the
     * turns a flavour buff's description prints, its visual cooldown to two decimals
     * ({@code …/actors/buffs/FlavourBuff.java:35-42}; {@code …/actors/buffs/Buff.java:136-138},
     * {@code :141-143}), carried only when the description the window shows contains them, which
     * one flavour buff's does not ({@code …/actors/buffs/Shadows.java:125-127}); a buff of another
     * kind prints its own numbers in its own words, which the schema does not carry. Two identical
     * icons draw twice; the schema lists a buff once.
     */
    static List<BuffView> buffs(Char ch) {
        List<BuffView> views = new ArrayList<>();
        for (Buff buff : ch.buffs()) {
            if (buff.icon() == BuffIndicator.NONE) {
                continue;
            }
            boolean timed = buff instanceof FlavourBuff
                    && buff.desc().contains(Messages.decimalFormat("#.##", buff.visualcooldown()));
            int hundredths = timed ? Math.max(0, Math.round(buff.visualcooldown() * 100f)) : 0;
            BuffView view = new BuffView(buff.name(), timed, hundredths);
            if (!views.contains(view)) {
                views.add(view);
            }
        }
        return views;
    }

    /** The hunger icon's state ({@code …/actors/buffs/Hunger.java:179-187}), never the value behind it. */
    static Hunger hunger(Hero hero) {
        com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger hunger =
                hero.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger.class);
        if (hunger == null) {
            return Hunger.NONE;
        }
        int icon = hunger.icon();
        return icon == BuffIndicator.STARVATION ? Hunger.STARVING : icon == BuffIndicator.HUNGER ? Hunger.HUNGRY : Hunger.NONE;
    }

    /** The category word a crystal chest's description prints for what is inside ({@code …/items/Heap.java:400-406}). */
    private static String category(Item inside) {
        if (inside instanceof Artifact) {
            return Messages.get(Heap.class, "artifact");
        } else if (inside instanceof Wand) {
            return Messages.get(Heap.class, "wand");
        } else {
            return Messages.get(Heap.class, "ring");
        }
    }

    /**
     * The fog level the fog of war paints on a cell ({@code …/tiles/FogOfWar.java:200-267}),
     * raised to what the examine window gives for a wall the fog paints opaque.
     *
     * <p>A cell that cannot be discovered, or that is neither in view, visited nor mapped, is
     * opaque ({@code :200-205}). A cell that is not a wall is painted its own level: in view,
     * visited, mapped ({@code :288-299}). A wall cell is painted by the cells its face belongs to
     * ({@code :210-267}): opaque on the bottom row; a wall with a wall below it is painted in two
     * halves, each the darkest of the cell, its side neighbour and, when that neighbour is a
     * wall, the cell below that neighbour, and opaque when the neighbour and the cell below it are
     * both walls or the cell is at the map's edge; a wall with a floor below it is the darkest of
     * the cell and the cell below. The cell's level is the lighter of its two halves, the part the
     * player sees. So a room's far wall is opaque until the corridor beyond is seen, even while
     * in view.
     *
     * <p>Such a wall is still visited or mapped, so the examine window opens on it and draws its
     * tile ({@code …/scenes/GameScene.java:1661-1667}; {@code …/windows/WndInfoCell.java:42-74});
     * it is emitted as visited or mapped rather than unknown, which is what the player can learn
     * of it. The fog's own gate stays in front of that step: a cell that cannot be discovered is
     * never visited or mapped in play and reads unknown whatever the arrays say.
     * {@code FogParityTest} holds every cell to the painted texture.
     */
    static Fog fog(Level level, int cell) {
        Fog painted = painted(level, cell);
        if (painted == Fog.UNKNOWN && level.discoverable[cell] && wall(level, cell)
                && (level.visited[cell] || level.mapped[cell])) {
            return level.visited[cell] ? Fog.VISITED : Fog.MAPPED;
        }
        return painted;
    }

    /** The level the fog of war paints, as {@code FogOfWar.updateTexture} decides it ({@code FogOfWar.java:200-267}). */
    private static Fog painted(Level level, int cell) {
        if (!level.discoverable[cell] || (!level.heroFOV[cell] && !level.visited[cell] && !level.mapped[cell])) {
            return Fog.UNKNOWN;
        }
        if (!wall(level, cell)) {
            return own(level, cell);
        }
        int width = level.width();
        if (cell + width >= level.length()) {
            return Fog.UNKNOWN;
        }
        if (!wall(level, cell + width)) {
            return darker(own(level, cell), own(level, cell + width));
        }
        Fog left = Fog.UNKNOWN;
        if (cell % width != 0) {
            if (wall(level, cell - 1)) {
                left = wall(level, cell + width - 1) ? Fog.UNKNOWN
                        : darker(own(level, cell), darker(own(level, cell + width - 1), own(level, cell - 1)));
            } else {
                left = darker(own(level, cell), own(level, cell - 1));
            }
        }
        Fog right = Fog.UNKNOWN;
        if ((cell + 1) % width != 0) {
            if (wall(level, cell + 1)) {
                right = wall(level, cell + width + 1) ? Fog.UNKNOWN
                        : darker(own(level, cell), darker(own(level, cell + width + 1), own(level, cell + 1)));
            } else {
                right = darker(own(level, cell), own(level, cell + 1));
            }
        }
        return lighter(left, right);
    }

    /** A cell's own level: in view, visited, mapped, or nothing ({@code FogOfWar.java:288-299}). */
    private static Fog own(Level level, int cell) {
        if (level.heroFOV[cell]) {
            return Fog.VISIBLE;
        } else if (level.visited[cell]) {
            return Fog.VISITED;
        } else if (level.mapped[cell]) {
            return Fog.MAPPED;
        } else {
            return Fog.UNKNOWN;
        }
    }

    /** Whether the fog treats the cell as a wall: the sheet's stitching set ({@code FogOfWar.java:284-286}). */
    private static boolean wall(Level level, int cell) {
        return DungeonTileSheet.wallStitcheable(level.map[cell]);
    }

    private static Fog darker(Fog a, Fog b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static Fog lighter(Fog a, Fog b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /**
     * What the terrain tilemap draws for a terrain, as a {@link Tile}: the sheet's direct table
     * first, then water and chasm, which are stitched from their neighbours, then the flat table
     * ({@code …/tiles/DungeonTerrainTilemap.java:42-56}; {@code …/tiles/DungeonTileSheet.java:414-465}).
     * A secret door reaches the wall's visual and a secret trap the floor's through those tables,
     * so no rule of Shatterfish's own decides what a secret looks like. The mine's crystal and
     * boulder share every sprite ({@code DungeonTileSheet.java:211-216}) and are told apart by the
     * cell's name ({@code …/levels/MiningLevel.java:227-235}), so they are the one pair mapped by
     * terrain. A terrain in none of the tables is a change of the tag and fails here.
     */
    static Tile tile(int terrain) {
        if (terrain == Terrain.MINE_CRYSTAL) {
            return Tile.MINE_CRYSTAL;
        } else if (terrain == Terrain.MINE_BOULDER) {
            return Tile.MINE_BOULDER;
        }
        Integer visual = DungeonTileSheet.directVisuals.get(terrain, null);
        if (visual == null && terrain == Terrain.WATER) {
            return Tile.WATER;
        } else if (visual == null && terrain == Terrain.CHASM) {
            return Tile.CHASM;
        }
        if (visual == null) {
            visual = DungeonTileSheet.directFlatVisuals.get(terrain, null);
        }
        if (visual == null) {
            throw new IllegalStateException("terrain " + terrain + " is in neither table of the tile sheet"
                    + " (DungeonTileSheet.java:414-465) and is neither water nor chasm; the tag has changed");
        }
        Tile tile = BY_VISUAL.get(visual);
        if (tile == null) {
            throw new IllegalStateException("the tile sheet draws terrain " + terrain + " with visual " + visual
                    + ", which no Tile names; the tag has changed");
        }
        return tile;
    }

    /**
     * The driver's own condition for an Input wait (AD-5; ADR-0015), so there is one definition:
     * the hero waits and nothing blocks the map, or a Prompt window is in front and the hero waits
     * under it, the resurrection window being the one a hero who is not ready answers
     * ({@code …/windows/WndResurrect.java:98-114}); any other window in front is a failure
     * (ADR-0006, Prompt).
     */
    private static void atInputWait() {
        Level level = Dungeon.level;
        Hero hero = Dungeon.hero;
        require(level != null && hero != null, "no Run is in progress");
        Window window = Windows.front();
        if (window == null) {
            require(HeadlessDriver.heroWaits(hero),
                    "the hero is not waiting for input: ready=" + hero.ready + ", action=" + hero.curAction + ", resting=" + hero.resting);
            require(!GameScene.interfaceBlockingHero(),
                    "the inventory is selecting an item (GameScene.java:1392-1402), which is not a wait");
        } else {
            require(Prompts.kind(window) != PromptKind.NONE,
                    "the window in front is not a Prompt: " + Prompts.describe(window) + "; any other window at an"
                            + " Input wait is a failure (ADR-0006)");
            require(HeadlessDriver.heroWaits(hero) || WndResurrect.instance != null,
                    "a Prompt is in front but the hero is not waiting under it: ready=" + hero.ready + ", action="
                            + hero.curAction + ", resting=" + hero.resting);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("the Observer runs only at an Input wait: " + message);
        }
    }
}
