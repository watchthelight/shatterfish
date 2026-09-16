package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.Ratmogrify;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.cleric.PowerOfMany;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Feint;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.huntress.SpiritHawk;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.rogue.ShadowClone;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.rogue.SmokeBomb;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Acidic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Albino;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredBrute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredStatue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bandit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bee;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CausticSlime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Crab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalSpire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalWisp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM201;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DemonSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.EbonyMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FetidRat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalCore;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSpinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollExile;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGuard;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollSapper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollTrickster;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GoldenMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Golem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Guard;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.HermitCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Monk;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.PhantomPiranha;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Piranha;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Pylon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RipperDemon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RotHeart;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RotLasher;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Senior;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Skeleton;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Slime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.SpectralNecromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Spinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.TormentedSpirit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogFist;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.ImpShopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.MirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.PrismaticImage;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.RatKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultMirror;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultTokenDoor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultBossElemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultDM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultDM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultElemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultGhoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultGolem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultRat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultShaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultSkeleton;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLivingEarth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GuardianTrap;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mobs table (story 2.2): every concrete mob class of the game, constructed by the game's
 * own initialisers under {@link GameContext} and read from the instance; the rolls read from the
 * declaring class's source as cited expressions; the loot read from the declaration; variants
 * for every depth and every challenge under which a field differs from the base at depth 1
 * with no challenge, checked to compose. The list of constructors is written here and
 * compile-checked; {@code CodexCompletenessTest} enumerates the game's classes and holds that the
 * two agree, so a mob the game adds or drops fails the build rather than the Codex. What a
 * constructor draws or takes from the hero is named here by class, and the same test enumerates
 * from bytecode which constructors read the hero, the statistics or a generator and holds that
 * the two lists agree.
 */
final class Mobs {

    /** The deepest floor a rotation is read for, the amulet floor. */
    static final int MAX_DEPTH = 26;

    /** Every concrete mob class, one constructor each, in the game's package order. */
    static final List<Supplier<Mob>> ALL = List.of(
            Acidic::new, Albino::new, ArmoredBrute::new, ArmoredStatue::new, Bandit::new, Bat::new, Bee::new, Brute::new,
            CausticSlime::new, Crab::new, CrystalGuardian::new, CrystalMimic::new, CrystalSpire::new, CrystalWisp::new,
            DM100::new, DM200::new, DM201::new, DM300::new, DemonSpawner::new, DwarfKing::new, DwarfKing.DKGhoul::new,
            DwarfKing.DKGolem::new, DwarfKing.DKMonk::new, DwarfKing.DKWarlock::new, EbonyMimic::new,
            Elemental.AllyNewBornElemental::new, Elemental.ChaosElemental::new, Elemental.FireElemental::new,
            Elemental.FrostElemental::new, Elemental.NewbornFireElemental::new, Elemental.ShockElemental::new, Eye::new,
            FetidRat::new, FungalCore::new, FungalSentry::new, FungalSpinner::new, Ghoul::new, Gnoll::new, GnollExile::new,
            GnollGeomancer::new, GnollGuard::new, GnollSapper::new, GnollTrickster::new, GoldenMimic::new, Golem::new, Goo::new,
            GreatCrab::new, Guard::new, HermitCrab::new, Mimic::new, Monk::new, Necromancer::new, Necromancer.NecroSkeleton::new,
            PhantomPiranha::new, Piranha::new, Pylon::new, Rat::new, RipperDemon::new, RotHeart::new, RotLasher::new, Scorpio::new,
            Senior::new, Shaman.BlueShaman::new, Shaman.PurpleShaman::new, Shaman.RedShaman::new, Skeleton::new, Slime::new,
            Snake::new, SpectralNecromancer::new, Spinner::new, Statue::new, Succubus::new, Swarm::new, Tengu::new, Thief::new,
            TormentedSpirit::new, Warlock::new, Wraith::new, YogDzewa::new, YogDzewa.Larva::new, YogDzewa.YogEye::new,
            YogDzewa.YogRipper::new, YogDzewa.YogScorpio::new, YogFist.BrightFist::new, YogFist.BurningFist::new,
            YogFist.DarkFist::new, YogFist.RottingFist::new, YogFist.RustedFist::new, YogFist.SoiledFist::new,
            Blacksmith::new, DirectableAlly::new, Ghost::new, Imp::new, ImpShopkeeper::new, MirrorImage::new, PrismaticImage::new,
            RatKing::new, Sheep::new, Shopkeeper::new, VaultLaser::new, VaultMirror::new, VaultSentry::new, VaultTokenDoor::new,
            Wandmaker::new,
            VaultBossElemental::new, VaultDM100::new, VaultDM200::new, VaultElemental.Fire::new, VaultElemental.Frost::new,
            VaultElemental.Shock::new, VaultGhoul::new, VaultGolem::new, VaultRat::new, VaultShaman::new, VaultSkeleton::new,
            // The mobs declared outside the mobs package: the heroes' summons, the wands' and
            // artifacts' allies, a room's and a trap's guardians.
            Ratmogrify.TransmogRat::new, PowerOfMany.LightAlly::new, Feint.AfterImage::new, SpiritHawk.HawkAlly::new,
            ShadowClone.ShadowAlly::new, SmokeBomb.NinjaLog::new, DriedRose.GhostHero::new, CorpseDust.DustWraith::new,
            WandOfLivingEarth.EarthGuardian::new, WandOfRegrowth.Lotus::new, WandOfWarding.Ward::new, WandOfWarding.Ward.WardSentry::new,
            SentryRoom.Sentry::new, GuardianTrap.Guardian::new);

    /**
     * The classes whose properties are decided by a draw at construction (the vault boss
     * elemental picks its element, {@code VaultBossElemental.java:95}); their properties are not
     * listed, the entry says so, and the draw is cited.
     */
    static final Set<Class<?>> RANDOM_PROPERTIES = Set.of(VaultBossElemental.class);

    /**
     * The numeric fields whose value a constructor reads from the hero of a Run, by class: the
     * rogue's decoy multiplies its hit points by a talent when a hero exists
     * ({@code SmokeBomb.java:175-176}). Such a field is dumped as zero and named, and not compared
     * across depths and challenges; a cold generation and one inside a Run then agree, which
     * {@code CodexLeakTest} holds.
     */
    static final Map<Class<?>, List<String>> HERO_DEPENDENT = Map.of(SmokeBomb.NinjaLog.class, List.of("ht"));

    /**
     * The classes whose stats the game sets after construction, so that the constructed values
     * are placeholders: the mimics by {@code setLevel} at spawn ({@code Mimic.java:259-266}), the
     * wraiths by {@code adjustStats} ({@code Wraith.java:85}), the bee at spawn
     * ({@code Bee.java:89}), and the summons and guardians whose real constructor or summoner
     * sets them. The entry says so; the values are as constructed.
     */
    static final Set<Class<?>> STATS_SET_LATER = Set.of(Mimic.class, GoldenMimic.class, CrystalMimic.class, EbonyMimic.class,
            Wraith.class, CorpseDust.DustWraith.class, TormentedSpirit.class, Bee.class, ShadowClone.ShadowAlly.class,
            MirrorImage.class, DriedRose.GhostHero.class, WandOfWarding.Ward.class, WandOfRegrowth.Lotus.class,
            WandOfLivingEarth.EarthGuardian.class, Ratmogrify.TransmogRat.class, SentryRoom.Sentry.class, GuardianTrap.Guardian.class);

    /**
     * What a constructor reads that is a Run's, a Profile's or a generator's, by class, with the
     * reason: {@code CodexCompletenessTest} finds the same set in the bytecode of every mob's
     * constructors and initialisers and holds that this list names each, so that a new read is
     * reviewed here before it can reach a table. The ones that reach a dumped field are in
     * {@link #HERO_DEPENDENT} or {@link #RANDOM_PROPERTIES}; the others land in fields the table
     * does not carry, or are draws whose result is not dumped.
     */
    static final Map<Class<?>, String> CONSTRUCTOR_READS = Map.ofEntries(
            Map.entry(SmokeBomb.NinjaLog.class, "hit points from the hero's talent (run-dependent, named)"),
            Map.entry(VaultBossElemental.class, "the element drawn (properties random, named)"),
            Map.entry(Thief.class, "the loot category drawn (loot random, named)"),
            Map.entry(DM200.class, "the loot category drawn (loot random, named)"),
            Map.entry(Golem.class, "the loot category drawn (loot random, named)"),
            Map.entry(CrystalWisp.class, "a draw for a field the table does not carry"),
            Map.entry(CrystalGuardian.class, "a draw for a field the table does not carry"),
            Map.entry(CrystalSpire.class, "a draw for a field the table does not carry"),
            Map.entry(PowerOfMany.LightAlly.class, "a draw for a field the table does not carry"),
            Map.entry(SpiritHawk.HawkAlly.class, "the hero's talents for view distance and speed, fields the table does not carry"),
            Map.entry(ShadowClone.ShadowAlly.class, "the hero for its real constructor's stats (stats set later, named)"),
            Map.entry(DM300.class, "a draw for a field the table does not carry"),
            Map.entry(GnollGeomancer.class, "a draw for a field the table does not carry"),
            Map.entry(GnollSapper.class, "a draw for a field the table does not carry"),
            Map.entry(Pylon.class, "a draw for a field the table does not carry"),
            Map.entry(VaultShaman.class, "a draw for a field the table does not carry"),
            Map.entry(YogDzewa.class, "the seeded depth and the spawners alive, for fields the table does not carry"));

    private static final String DAMAGE = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?int\\s+damageRoll\\s*\\(\\s*\\)";
    private static final String ATTACK = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?int\\s+attackSkill\\s*\\(\\s*Char\\s+\\w+\\s*\\)";
    private static final String DR = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?int\\s+drRoll\\s*\\(\\s*\\)";
    private static final String DEFENSE = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?int\\s+defenseSkill\\s*\\(\\s*Char\\s+\\w+\\s*\\)";
    private static final String CREATE_LOOT = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?Item\\s+createLoot\\s*\\(\\s*\\)";
    private static final String LOOT_CHANCE_METHOD = "^\\s*(?:@\\w+\\s+)*(?:public|protected)\\s+(?:final\\s+)?float\\s+lootChance\\s*\\(\\s*\\)";
    private static final Pattern SUPER_DR = Pattern.compile("^return\\s+super\\.drRoll\\(\\)\\s*\\+\\s*(.*)$");
    private static final Pattern NORMAL = Pattern.compile("^return\\s+Random\\.NormalIntRange\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\);$");
    private static final Pattern INT_RANGE = Pattern.compile("^return\\s+Random\\.IntRange\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\);$");
    private static final Pattern INT = Pattern.compile("^return\\s+Random\\.Int\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\);$");
    private static final Pattern CONSTANT = Pattern.compile("^return\\s+(-?\\d+);$");
    // The field's declaration (with its modifier and type) or an initialiser's assignment, on a
    // member line; a method's local of the same name (Mob.lootChance() has one) is not a member.
    private static final Pattern LOOT = Pattern.compile("^\\s*(?:(?:protected|public|private)\\s+Object\\s+)?loot\\s*=\\s*([^;]+);\\s*(?://.*)?$");
    private static final Pattern LOOT_CHANCE = Pattern.compile("^\\s*(?:(?:protected|public|private)\\s+float\\s+)?lootChance\\s*=\\s*([^;]+);\\s*(?://.*)?$");
    private static final Pattern DRAW = Pattern.compile("\\bRandom\\.\\w+\\(");

    private Mobs() {
    }

    /** Every mob's entry, in {@link #ALL}'s order. */
    static List<Codex.MobEntry> entries(Path root) {
        List<Codex.MobEntry> entries = new ArrayList<>();
        for (Supplier<Mob> make : ALL) {
            entries.add(entry(root, make));
        }
        return entries;
    }

    /** Every mob's table name by its simple name, for the rotation to name classes as the table does. */
    static Map<String, String> canonicalNames() {
        TreeMap<String, String> names = new TreeMap<>();
        for (Supplier<Mob> make : ALL) {
            Class<?> type = GameContext.under(1, 0, make).getClass();
            String simple = type.getSimpleName();
            if (names.put(simple, Sources.name(type)) != null) {
                throw new IllegalStateException("two mob classes share the simple name " + simple);
            }
        }
        return names;
    }

    /** One mob's entry: the base at depth 1 with no challenge, and its variants, checked to compose. */
    static Codex.MobEntry entry(Path root, Supplier<Mob> make) {
        Mob base = construct(make, 1, "");
        Class<?> type = base.getClass();
        Sources.Body body = Sources.body(root, type);
        boolean randomProperties = RANDOM_PROPERTIES.contains(type);
        List<String> runDependent = HERO_DEPENDENT.getOrDefault(type, List.of());
        List<Codex.Variant> variants = new ArrayList<>();
        Map<Integer, List<Codex.Field>> byDepth = new TreeMap<>();
        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            List<Codex.Field> differing = differing(base, construct(make, depth, ""), randomProperties, runDependent);
            if (!differing.isEmpty()) {
                variants.add(new Codex.Variant(depth, "", differing));
                byDepth.put(depth, differing);
            }
        }
        Map<Challenge, List<Codex.Field>> byChallenge = new TreeMap<>();
        for (Challenge challenge : Challenge.values()) {
            List<Codex.Field> differing = differing(base, construct(make, 1, challenge.name()), randomProperties, runDependent);
            if (!differing.isEmpty()) {
                variants.add(new Codex.Variant(1, challenge.name(), differing));
                byChallenge.put(challenge, differing);
            }
        }
        // A depth's variant and a challenge's variant compose, or the table cannot say what the mob is under both.
        for (Map.Entry<Challenge, List<Codex.Field>> challenge : byChallenge.entrySet()) {
            for (Map.Entry<Integer, List<Codex.Field>> depth : byDepth.entrySet()) {
                Mob both = construct(make, depth.getKey(), challenge.getKey().name());
                Map<String, String> expected = new TreeMap<>();
                for (Codex.Field field : depth.getValue()) {
                    expected.put(field.name(), field.value());
                }
                for (Codex.Field field : challenge.getValue()) {
                    expected.put(field.name(), field.value());
                }
                Map<String, String> actual = new TreeMap<>();
                for (Codex.Field field : differing(base, both, randomProperties, runDependent)) {
                    actual.put(field.name(), field.value());
                }
                if (!expected.equals(actual)) {
                    throw new IllegalStateException(Sources.name(type) + " at depth " + depth.getKey() + " under " + challenge.getKey()
                            + " is " + actual + ", not the composition " + expected + "; the variant schema cannot say it");
                }
            }
        }
        List<Codex.Citation> draws = new ArrayList<>();
        List<Integer> drawLines = new ArrayList<>(body.memberLines());
        drawLines.addAll(body.constructorLines());
        for (int line : drawLines) {
            if (DRAW.matcher(Sources.stripComment(body.lines().get(line))).find()) {
                draws.add(body.citation(line));
            }
        }
        Codex.Loot loot = loot(root, type);
        if (loot.random() && !draws.contains(loot.citation())) {
            draws.add(loot.citation());
        }
        Sources.Declared defense = Sources.declared(root, type, Char.class, DEFENSE);
        boolean customDefense = defense != null && defense.owner() != Mob.class && defense.owner() != Char.class;
        return new Codex.MobEntry(Sources.name(type), Generate.api(Alignment::valueOf, base.alignment.name(), "alignment", "org.shatterfish.api.Alignment"),
                randomProperties ? List.of() : properties(base), randomProperties, runDependent, STATS_SET_LATER.contains(type),
                customDefense, draws,
                runDependent.contains("ht") ? 0 : base.HT, runDependent.contains("defenseSkill") ? 0 : base.defenseSkill,
                runDependent.contains("exp") ? 0 : base.EXP, runDependent.contains("maxLvl") ? 0 : base.maxLvl,
                roll(root, type, DAMAGE), roll(root, type, ATTACK), dr(root, type), loot,
                variants, body.citation(body.from() == 0 ? body.declaration(type.getSimpleName()) : body.from()));
    }

    /** A construction under the context, its failure naming the class, the depth and the challenge. */
    private static Mob construct(Supplier<Mob> make, int depth, String challenge) {
        int mask = challenge.isEmpty() ? 0 : Generate.mask(Challenge.valueOf(challenge));
        try {
            return GameContext.under(depth, mask, make);
        } catch (RuntimeException e) {
            throw new IllegalStateException("a mob could not be constructed at depth " + depth
                    + (challenge.isEmpty() ? "" : " under " + challenge) + ": " + e, e);
        }
    }

    private static List<String> properties(Mob mob) {
        TreeSet<String> names = new TreeSet<>();
        for (Char.Property property : mob.properties()) {
            names.add(property.name());
        }
        return new ArrayList<>(names);
    }

    /** The fields of {@code other} that differ from {@code base}, by name, as text; the named ones skipped. */
    static List<Codex.Field> differing(Mob base, Mob other, boolean randomProperties, List<String> skip) {
        List<Codex.Field> fields = new ArrayList<>();
        if (!skip.contains("ht") && base.HT != other.HT) {
            fields.add(new Codex.Field("ht", Integer.toString(other.HT)));
        }
        if (!skip.contains("defenseSkill") && base.defenseSkill != other.defenseSkill) {
            fields.add(new Codex.Field("defenseSkill", Integer.toString(other.defenseSkill)));
        }
        if (!skip.contains("exp") && base.EXP != other.EXP) {
            fields.add(new Codex.Field("exp", Integer.toString(other.EXP)));
        }
        if (!skip.contains("maxLvl") && base.maxLvl != other.maxLvl) {
            fields.add(new Codex.Field("maxLvl", Integer.toString(other.maxLvl)));
        }
        if (base.alignment != other.alignment) {
            fields.add(new Codex.Field("alignment", other.alignment.name()));
        }
        if (!randomProperties && !properties(base).equals(properties(other))) {
            fields.add(new Codex.Field("properties", String.join(",", properties(other))));
        }
        return fields;
    }

    /** The roll a method anchored by {@code anchor} declares for {@code type}, read up to {@code Char}. */
    static Codex.Roll roll(Path root, Class<?> type, String anchor) {
        Sources.Declared declared = Sources.declared(root, type, Char.class, anchor);
        if (declared == null) {
            throw new IllegalStateException(type.getName() + " declares no method matching " + anchor + " up to Char");
        }
        return classify(Sources.returns(declared.block()), declared.block().citation(declared.line()));
    }

    /**
     * The damage reduction: as a roll, except that {@code super.drRoll() + X} is a plain roll only
     * when the parent's roll is {@code Char}'s own (zero without a bark skin); over any other
     * parent it is the sum of two, which is OTHER with the text.
     */
    static Codex.Roll dr(Path root, Class<?> type) {
        Sources.Declared declared = Sources.declared(root, type, Char.class, DR);
        if (declared == null) {
            throw new IllegalStateException(type.getName() + " declares no drRoll up to Char");
        }
        List<String> returns = Sources.returns(declared.block());
        Codex.Citation citation = declared.block().citation(declared.line());
        if (returns.size() == 1) {
            Matcher chained = SUPER_DR.matcher(returns.get(0));
            if (chained.matches()) {
                Sources.Declared parent = declared.owner().getSuperclass() == null ? null
                        : Sources.declared(root, declared.owner().getSuperclass(), Char.class, DR);
                if (parent != null && parent.owner() == Char.class) {
                    return classify(List.of("return " + chained.group(1)), citation);
                }
                return new Codex.Roll(Codex.RollKind.OTHER, 0, 0, returns.get(0), citation);
            }
        }
        return classify(returns, citation);
    }

    /** The kind and bounds of one return statement, or OTHER with the text for anything else. */
    static Codex.Roll classify(List<String> returns, Codex.Citation citation) {
        if (returns.size() == 1) {
            String line = returns.get(0);
            Matcher normal = NORMAL.matcher(line);
            if (normal.matches()) {
                return new Codex.Roll(Codex.RollKind.NORMAL, Integer.parseInt(normal.group(1)), Integer.parseInt(normal.group(2)), line, citation);
            }
            Matcher range = INT_RANGE.matcher(line);
            if (range.matches()) {
                return new Codex.Roll(Codex.RollKind.UNIFORM, Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)), line, citation);
            }
            Matcher exclusive = INT.matcher(line);
            if (exclusive.matches()) {
                return new Codex.Roll(Codex.RollKind.UNIFORM, Integer.parseInt(exclusive.group(1)), Integer.parseInt(exclusive.group(2)) - 1, line, citation);
            }
            Matcher constant = CONSTANT.matcher(line);
            if (constant.matches()) {
                int value = Integer.parseInt(constant.group(1));
                return new Codex.Roll(Codex.RollKind.CONSTANT, value, value, line, citation);
            }
            return new Codex.Roll(Codex.RollKind.OTHER, 0, 0, line, citation);
        }
        return new Codex.Roll(Codex.RollKind.OTHER, 0, 0, returns.isEmpty() ? "(no return)" : String.join(" | ", returns), citation);
    }

    /** The loot as the nearest declaring class's members state it, with the override flags. */
    static Codex.Loot loot(Path root, Class<?> type) {
        String declaration = null;
        Codex.Citation citation = null;
        String chanceExpression = null;
        int chance = -1;
        for (Class<?> c = type; c != null && c != Char.class; c = c.getSuperclass()) {
            Sources.Body body = Sources.body(root, c);
            if (declaration == null) {
                int line = body.firstMember(LOOT);
                if (line >= 0) {
                    Matcher m = LOOT.matcher(body.lines().get(line));
                    m.matches();
                    declaration = m.group(1).trim();
                    citation = body.citation(line);
                }
            }
            if (chanceExpression == null) {
                int line = body.firstMember(LOOT_CHANCE);
                if (line >= 0) {
                    Matcher m = LOOT_CHANCE.matcher(body.lines().get(line));
                    m.matches();
                    chanceExpression = m.group(1).trim();
                    chance = Sources.thousandths(chanceExpression);
                }
            }
            if (declaration != null && chanceExpression != null) {
                break;
            }
        }
        if (declaration == null || chanceExpression == null) {
            throw new IllegalStateException(type.getName() + " declares no loot or no loot chance up to Mob");
        }
        Codex.LootKind kind;
        String name;
        Matcher m;
        if (declaration.equals("null")) {
            kind = Codex.LootKind.NONE;
            name = "";
        } else if ((m = Pattern.compile("^Random\\.oneOf\\((.*)\\)$").matcher(declaration)).matches()) {
            kind = Codex.LootKind.RANDOM;
            name = m.group(1).replace("Generator.Category.", "").replace(" ", "");
        } else if ((m = Pattern.compile("^Generator\\.Category\\.(\\w+)$").matcher(declaration)).matches()) {
            kind = Codex.LootKind.CATEGORY;
            name = m.group(1);
        } else if ((m = Pattern.compile("^(\\w+)\\.class$").matcher(declaration)).matches()) {
            kind = Codex.LootKind.CLASS;
            name = m.group(1);
        } else if ((m = Pattern.compile("^new (\\w+)\\(.*$").matcher(declaration)).matches()) {
            kind = Codex.LootKind.ITEM;
            name = m.group(1);
        } else {
            kind = Codex.LootKind.ITEM;
            name = declaration;
        }
        Sources.Declared createLoot = Sources.declared(root, type, Mob.class, CREATE_LOOT);
        Sources.Declared lootChance = Sources.declared(root, type, Mob.class, LOOT_CHANCE_METHOD);
        boolean customLoot = createLoot != null && createLoot.owner() != Mob.class;
        boolean customChance = lootChance != null && lootChance.owner() != Mob.class;
        return new Codex.Loot(kind, name, declaration, chance, chanceExpression, kind == Codex.LootKind.RANDOM, customLoot, customChance, citation);
    }
}
