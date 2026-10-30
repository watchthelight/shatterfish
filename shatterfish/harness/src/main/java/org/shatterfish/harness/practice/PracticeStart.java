package org.shatterfish.harness.practice;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Longsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Mace;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Quarterstaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Rapier;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Spear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Whip;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;
import com.watabou.utils.Random;
import org.shatterfish.api.HeroClass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The game-side half of a practice start: puts a hero on a boss floor with the kit a
 * {@link KitSpec} describes, using the game's own classes and nothing of upstream edited (ADR-0024).
 *
 * <p>The sequence is the one a descent runs, shortened: {@code NewGame.begin} has called
 * {@code Dungeon.init} (the hero, the seed's potion colours and scroll labels, the statistics),
 * this raises the hero to the kit and sets the depth to the boss's, and {@code NewGame.begin} goes on
 * to build the floor with {@code Dungeon.newLevel} and place the hero with {@code switchLevel(level,
 * -1)}, which is the entrance, as the first floor's start is
 * ({@code core/.../scenes/InterlevelScene.java:622-648}). A boss floor reads nothing of the floors
 * above it: {@code Level.create} skips its item block on a boss floor
 * ({@code core/.../levels/Level.java:224}) and the floor's seed is a function of the Run's seed and
 * the depth alone ({@code core/.../Dungeon.java:414-431}), so the floor a practice Run builds is the
 * floor a real Run of the same seed reaches.
 *
 * <p>What it sets, and how: the level through {@code Hero.earnExp}, so that health, accuracy and
 * evasion follow the game's own level-up ({@code Hero.java:2087-2097}); strength as the field the
 * potion of strength raises ({@code PotionOfStrength.java:46}); each upgrade through
 * {@code Item.upgrade}, one at a time, as a scroll does it. It sets nothing the hero could not
 * have: no item of a class outside {@link KitSpec#ITEMS}, no talent, no buff.
 */
public final class PracticeStart {

    private PracticeStart() {
    }

    /** Every item class a kit may name, by the simple name {@link KitSpec#ITEMS} holds. */
    private static final Map<String, Class<? extends Item>> CLASSES = new LinkedHashMap<>();

    /** How each is built: a constructor reference, so that no reflection is needed (HarnessReflectionTest). */
    private static final Map<String, Supplier<? extends Item>> MAKERS = new LinkedHashMap<>();

    private static <T extends Item> void put(String name, Class<T> type, Supplier<T> maker) {
        CLASSES.put(name, type);
        MAKERS.put(name, maker);
    }

    static {
        put("ClothArmor", ClothArmor.class, ClothArmor::new);
        put("LeatherArmor", LeatherArmor.class, LeatherArmor::new);
        put("MailArmor", MailArmor.class, MailArmor::new);
        put("ScaleArmor", ScaleArmor.class, ScaleArmor::new);
        put("PlateArmor", PlateArmor.class, PlateArmor::new);
        put("WornShortsword", WornShortsword.class, WornShortsword::new);
        put("Dagger", Dagger.class, Dagger::new);
        put("Gloves", Gloves.class, Gloves::new);
        put("Rapier", Rapier.class, Rapier::new);
        put("Cudgel", Cudgel.class, Cudgel::new);
        put("MagesStaff", MagesStaff.class, MagesStaff::new);
        put("Shortsword", Shortsword.class, Shortsword::new);
        put("Sword", Sword.class, Sword::new);
        put("Mace", Mace.class, Mace::new);
        put("Spear", Spear.class, Spear::new);
        put("Longsword", Longsword.class, Longsword::new);
        put("Quarterstaff", Quarterstaff.class, Quarterstaff::new);
        put("Whip", Whip.class, Whip::new);
        put("SpiritBow", SpiritBow.class, SpiritBow::new);
        put("CloakOfShadows", CloakOfShadows.class, CloakOfShadows::new);
        put("HolyTome", HolyTome.class, HolyTome::new);
        put("Food", Food.class, Food::new);
        put("PotionOfHealing", PotionOfHealing.class, PotionOfHealing::new);
        put("ThrowingStone", ThrowingStone.class, ThrowingStone::new);
        put("ThrowingKnife", ThrowingKnife.class, ThrowingKnife::new);
        put("ThrowingSpike", ThrowingSpike.class, ThrowingSpike::new);
    }

    /** The names this class can build, which a harness test holds equal to {@link KitSpec#ITEMS}. */
    public static java.util.List<String> buildable() {
        return java.util.List.copyOf(CLASSES.keySet());
    }

    /**
     * Raises {@code Dungeon.hero} to {@code kit} and sets the depth and branch to {@code boss}'s, between
     * {@code Dungeon.init} and the building of the floor. The caller owns the generator stack; whatever
     * this draws is drawn inside a generator seeded from the kit and the Run's seed, so the draws are
     * a function of the tuple and the profile and not of what came before.
     */
    public static void arrive(PracticeBoss boss, KitSpec kit) {
        Hero hero = Dungeon.hero;
        if (hero == null) {
            throw new IllegalStateException("Dungeon.init comes first: the hero is the one it made");
        }
        Random.pushGenerator(Dungeon.seed * 31 + kit.canonical().hashCode());
        try {
            // Level, through the game's own experience code: the experience from level 1 to the kit's
            // level, which the loop in earnExp spends one level at a time (Hero.java:2077-2097).
            int experience = 0;
            for (int level = hero.lvl; level < kit.level(); level++) {
                experience += Hero.maxExp(level);
            }
            hero.earnExp(experience, Hero.class);
            hero.STR = kit.strength();

            for (KitSpec.Piece piece : kit.equipment()) {
                equip(hero, piece);
            }
            for (KitSpec.Piece piece : kit.pack()) {
                carry(hero, piece);
            }

            // What the floors above are taken to have given out, so that a question the game asks of
            // its own history (a later floor's "is a potion of strength due", asked if the Run goes on
            // below the boss) gets the answer a real descent would: Dungeon.java:529-562.
            Dungeon.LimitedDrops.STRENGTH_POTIONS.count = kit.strengthPotionsDropped();
            Dungeon.LimitedDrops.UPGRADE_SCROLLS.count = kit.upgradeScrollsDropped();
            Dungeon.LimitedDrops.ARCANE_STYLI.count = kit.strengthPotionsDropped() / 2;
            Dungeon.LimitedDrops.LAB_ROOM.count = kit.strengthPotionsDropped() / 2;
        } finally {
            Random.popGenerator();
        }
        // Dungeon.init left the hero on depth 1 of branch 0; the floor is built for the depth in force
        // when Dungeon.newLevel runs (Dungeon.java:297-355).
        Dungeon.depth = boss.depth();
        Dungeon.branch = 0;
    }

    private static Class<? extends Item> classOf(String name) {
        Class<? extends Item> found = CLASSES.get(name);
        if (found == null) {
            throw new IllegalArgumentException("a kit cannot build " + name + "; it builds " + CLASSES.keySet());
        }
        return found;
    }

    private static Item make(Class<? extends Item> type) {
        return MAKERS.get(type.getSimpleName()).get();
    }

    private static void upgrade(Item item, int times) {
        for (int i = 0; i < times; i++) {
            item.upgrade();
        }
    }

    /** One worn or wielded piece: the hero's own if the class matches, else a new one in its place. */
    private static void equip(Hero hero, KitSpec.Piece piece) {
        Class<? extends Item> type = classOf(piece.item());
        switch (piece.slot()) {
            case WEAPON -> {
                if (hero.belongings.weapon == null || hero.belongings.weapon.getClass() != type) {
                    Weapon made = (Weapon) make(type);
                    made.identify();
                    hero.belongings.weapon = made;
                    made.activate(hero);
                }
                upgrade(hero.belongings.weapon, piece.level());
            }
            case ARMOR -> {
                if (hero.belongings.armor == null || hero.belongings.armor.getClass() != type) {
                    Armor made = (Armor) make(type);
                    made.identify();
                    hero.belongings.armor = made;
                }
                upgrade(hero.belongings.armor, piece.level());
            }
            case ARTIFACT -> {
                if (hero.belongings.artifact == null || hero.belongings.artifact.getClass() != type) {
                    Artifact made = (Artifact) make(type);
                    made.identify();
                    hero.belongings.artifact = made;
                    made.activate(hero);
                }
                upgrade(hero.belongings.artifact, piece.level());
            }
            default -> throw new IllegalArgumentException("equipment goes in a slot: " + piece);
        }
    }

    /** One carried piece: the stack the hero already has, set to the kit's count, or a new one collected. */
    private static void carry(Hero hero, KitSpec.Piece piece) {
        Class<? extends Item> type = classOf(piece.item());
        Item held = hero.belongings.getItem(type);
        if (held == null) {
            held = make(type);
            held.identify();
            held.quantity(piece.quantity());
            held.collect();
        } else {
            held.quantity(piece.quantity());
        }
        upgrade(held, piece.level());
    }

    /** The game's hero class for an api one; the two enums share their names. */
    public static com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass gameClass(HeroClass heroClass) {
        return com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass.valueOf(heroClass.name());
    }
}
