package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.Ratmogrify;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.cleric.PowerOfMany;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Feint;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.huntress.SpiritHawk;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.rogue.ShadowClone;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.rogue.SmokeBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLivingEarth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GuardianTrap;
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
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mobs table (story 2.2): every concrete mob class of the game, constructed by the game's
 * own initialisers under {@link GameContext} and read from the instance; the rolls read from the
 * declaring class's source as cited expressions; the loot read from the declaration; variants
 * for every depth and every challenge under which a field differs from the base at depth 1
 * with no challenge. The list of constructors is written here and compile-checked;
 * {@code CodexCompletenessTest} enumerates the game's classes and holds that the two agree, so
 * a mob the game adds or drops fails the build rather than the Codex.
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
     * elemental picks its element, {@code VaultBossElemental.java}); their properties are not
     * listed, and the entry says so.
     */
    static final Set<Class<?>> RANDOM_PROPERTIES = Set.of(VaultBossElemental.class);

    /**
     * The fields whose value a constructor reads from the hero of a Run, by class: the rogue's
     * decoy multiplies its hit points by a talent when a hero exists
     * ({@code SmokeBomb.java:175-176}). Such a field is dumped as zero and named, and not compared
     * across depths and challenges; a cold generation and one inside a Run then agree, which
     * {@code CodexLeakTest} holds.
     */
    static final Map<Class<?>, List<String>> HERO_DEPENDENT = Map.of(SmokeBomb.NinjaLog.class, List.of("ht"));

    private static final String DAMAGE = "^\\s*public int damageRoll\\s*\\(\\s*\\)";
    private static final String ATTACK = "^\\s*public int attackSkill\\s*\\(\\s*Char\\s+\\w+\\s*\\)";
    private static final String DR = "^\\s*public int drRoll\\s*\\(\\s*\\)";
    private static final String CREATE_LOOT = "^\\s*public Item createLoot\\s*\\(\\s*\\)";
    private static final String LOOT_CHANCE_METHOD = "^\\s*public float lootChance\\s*\\(\\s*\\)";
    private static final Pattern NORMAL = Pattern.compile("^return\\s+(?:super\\.drRoll\\(\\)\\s*\\+\\s*)?Random\\.NormalIntRange\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\);$");
    private static final Pattern UNIFORM = Pattern.compile("^return\\s+(?:super\\.drRoll\\(\\)\\s*\\+\\s*)?Random\\.(?:IntRange|Int)\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\);$");
    private static final Pattern CONSTANT = Pattern.compile("^return\\s+(-?\\d+);$");
    // The field's declaration (with its modifier and type) or an initialiser's assignment; a
    // method's local of the same name (Mob.lootChance() has one) has a type and no modifier and
    // matches neither.
    private static final Pattern LOOT = Pattern.compile("^\\s*(?:(?:protected|public|private)\\s+Object\\s+)?loot\\s*=\\s*(.+);\\s*(?://.*)?$");
    private static final Pattern LOOT_CHANCE = Pattern.compile("^\\s*(?:(?:protected|public|private)\\s+float\\s+)?lootChance\\s*=\\s*(.+?);\\s*(?://.*)?$");

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

    /** One mob's entry: the base at depth 1 with no challenge, and its variants. */
    static Codex.MobEntry entry(Path root, Supplier<Mob> make) {
        Mob base = GameContext.under(1, 0, make);
        Class<?> type = base.getClass();
        Sources.Body body = Sources.body(root, type);
        boolean randomProperties = RANDOM_PROPERTIES.contains(type);
        List<String> runDependent = HERO_DEPENDENT.getOrDefault(type, List.of());
        List<Codex.Variant> variants = new ArrayList<>();
        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            List<Codex.Field> differing = differing(base, GameContext.under(depth, 0, make), randomProperties, runDependent);
            if (!differing.isEmpty()) {
                variants.add(new Codex.Variant(depth, "", differing));
            }
        }
        for (Challenge challenge : Challenge.values()) {
            List<Codex.Field> differing = differing(base, GameContext.under(1, Generate.mask(challenge), make), randomProperties, runDependent);
            if (!differing.isEmpty()) {
                variants.add(new Codex.Variant(1, challenge.name(), differing));
            }
        }
        return new Codex.MobEntry(Sources.name(type), Alignment.valueOf(base.alignment.name()),
                randomProperties ? List.of() : properties(base), randomProperties, runDependent,
                runDependent.contains("ht") ? 0 : base.HT, runDependent.contains("defenseSkill") ? 0 : base.defenseSkill,
                runDependent.contains("exp") ? 0 : base.EXP, runDependent.contains("maxLvl") ? 0 : base.maxLvl,
                roll(root, type, DAMAGE), roll(root, type, ATTACK), roll(root, type, DR), loot(root, type),
                variants, body.citation(body.from() == 0 ? body.declaration(type.getSimpleName()) : body.from()));
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
        if (!skip.contains("alignment") && base.alignment != other.alignment) {
            fields.add(new Codex.Field("alignment", other.alignment.name()));
        }
        if (!skip.contains("properties") && !randomProperties && !properties(base).equals(properties(other))) {
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
        List<String> returns = Sources.returns(declared.block());
        Codex.Citation citation = declared.block().citation(declared.line());
        if (returns.size() == 1) {
            String line = returns.get(0);
            Matcher normal = NORMAL.matcher(line);
            if (normal.matches()) {
                return new Codex.Roll(Codex.RollKind.NORMAL, Integer.parseInt(normal.group(1)), Integer.parseInt(normal.group(2)), line, citation);
            }
            Matcher uniform = UNIFORM.matcher(line);
            if (uniform.matches()) {
                return new Codex.Roll(Codex.RollKind.UNIFORM, Integer.parseInt(uniform.group(1)), Integer.parseInt(uniform.group(2)), line, citation);
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

    /** The loot as the nearest declaring class states it, with the override flags. */
    static Codex.Loot loot(Path root, Class<?> type) {
        String declaration = null;
        Codex.Citation citation = null;
        String chanceExpression = "0";
        int chance = 0;
        boolean chanceFound = false;
        for (Class<?> c = type; c != null && c != Char.class; c = c.getSuperclass()) {
            Sources.Body body = Sources.body(root, c);
            if (declaration == null) {
                int line = body.first(LOOT);
                if (line >= 0) {
                    Matcher m = LOOT.matcher(body.lines().get(line));
                    m.matches();
                    declaration = m.group(1).trim();
                    citation = body.citation(line);
                }
            }
            if (!chanceFound) {
                int line = body.first(LOOT_CHANCE);
                if (line >= 0) {
                    Matcher m = LOOT_CHANCE.matcher(body.lines().get(line));
                    m.matches();
                    chanceExpression = m.group(1).trim();
                    chance = Sources.thousandths(chanceExpression);
                    chanceFound = true;
                }
            }
            if (declaration != null && chanceFound) {
                break;
            }
        }
        if (declaration == null) {
            throw new IllegalStateException(type.getName() + " declares no loot up to Mob");
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
        } else if ((m = Pattern.compile("^new (\\w+)\\(.*\\)$").matcher(declaration)).matches()) {
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
        return new Codex.Loot(kind, name, chance, chanceExpression, kind == Codex.LootKind.RANDOM, customLoot, customChance, citation);
    }
}
