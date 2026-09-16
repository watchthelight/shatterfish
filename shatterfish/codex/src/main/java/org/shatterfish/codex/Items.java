package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.HolyLance;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultMirror;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.ArcaneResin;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Honeypot;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown;
import com.shatteredpixel.shatteredpixeldungeon.items.LiquidMetal;
import com.shatteredpixel.shatteredpixeldungeon.items.LostBackpack;
import com.shatteredpixel.shatteredpixeldungeon.items.Stylus;
import com.shatteredpixel.shatteredpixeldungeon.items.TengusMask;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClericArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.DuelistArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.HuntressArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MageArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.RogueArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.WarriorArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.AlchemistsToolkit;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CapeOfThorns;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.EtherealChains;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.LloydsBeacon;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.MasterThievesArmband;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.SandalsOfNature;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.SkeletonKey;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TalismanOfForesight;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TimekeepersHourglass;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.UnstableSpellbook;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Firebomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.FlashBangBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.FrostBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.HolyBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Noisemaker;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.SmokeBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.WoollyBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Berry;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ChargrilledMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.FrozenCarpaccio;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.PhantomMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SupplyRation;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.AlchemyPage;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.GuidePage;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.Guidebook;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.RegionLorePage;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.WornKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLiquidFlame;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfMindVision;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfParalyticGas;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.BlizzardBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.CausticBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.InfernalBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.ShockingBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.UnstableBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfAquaticRejuvenation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfArcaneArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfDragonsBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfFeatherFall;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfHoneyedHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfIcyTouch;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfToxicEssence;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCleansing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfDivineInspiration;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfDragonsBreath;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfEarthenArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfMagicalSight;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfMastery;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShielding;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShroudingFog;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfSnapFreeze;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStamina;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStormClouds;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CeremonialCandle;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.DarkGold;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.DwarfToken;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Embers;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.EscapeCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.GooBlob;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.ImpStatue;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.MetalShard;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Pickaxe;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.VaultBeacon;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.BowFragment;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.BrokenHilt;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.BrokenStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.CloakScrap;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.SealShard;
import com.shatteredpixel.shatteredpixeldungeon.items.remains.TornPage;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfArcana;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfElements;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEnergy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfFuror;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfTenacity;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfWealth;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRecharging;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRetribution;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfAntiMagic;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfChallenge;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfDivination;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfDread;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfForesight;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMetamorphosis;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMysticalEnergy;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfPassage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfPrismaticImage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfPsionicBlast;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfSirensSong;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.BeaconOfReturning;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.CurseInfusion;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.MagicalInfusion;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.PhaseShift;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.ReclaimTrap;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Recycle;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.SummonElemental;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.TelekineticGrab;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.UnstableSpell;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.WildEnergy;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAggression;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAugmentation;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlast;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlink;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfClairvoyance;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfDeepSleep;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfDetectMagic;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFear;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFlock;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfShock;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ChaoticCenser;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.CrackedSpyglass;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.DimensionalSundial;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ExoticCrystals;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.EyeOfNewt;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.FerretTuft;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.MimicTooth;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.MossyClump;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ParchmentScrap;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.PetrifiedSeed;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.RatSkull;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.SaltCube;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ShardOfOblivion;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ThirteenLeafClover;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrapMechanism;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.VialOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.WondrousResin;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfBlastWave;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorrosion;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorruption;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfDisintegration;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLivingEarth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.curses.Explosive;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.AssassinsBlade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.BattleAxe;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Crossbow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dirk;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Flail;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gauntlet;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Glaive;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greataxe;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greatshield;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greatsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.HandAxe;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Katana;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Longsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Mace;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Quarterstaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Rapier;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.RoundShield;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.RunicBlade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sai;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Scimitar;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sickle;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Spear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WarHammer;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WarScythe;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Whip;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Bolas;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.FishingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ForceCube;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.HeavyBoomerang;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Javelin;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Kunai;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Shuriken;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingClub;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingHammer;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Tomahawk;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Trident;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.AdrenalineDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.BlindingDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.ChillingDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.CleansingDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.Dart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.DisplacingDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.HealingDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.HolyDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.IncendiaryDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.ParalyticDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.PoisonDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.RotDart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.ShockingDart;
import com.shatteredpixel.shatteredpixeldungeon.plants.BlandfruitBush;
import com.shatteredpixel.shatteredpixeldungeon.plants.Blindweed;
import com.shatteredpixel.shatteredpixeldungeon.plants.Earthroot;
import com.shatteredpixel.shatteredpixeldungeon.plants.Fadeleaf;
import com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom;
import com.shatteredpixel.shatteredpixeldungeon.plants.Icecap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Mageroyal;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sorrowmoss;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Stormvine;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.plants.Swiftthistle;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ScorpioSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.TenguSprite;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The items (story 2.3): every concrete item class a player can meet, one entry each. An item
 * whose class can load on the generator's classpath is constructed under {@link GameContext} and
 * asked its value and the actions it offers a fresh, unequipped instance to a bare hero; the
 * three identifiable families and their exotics cannot load, since their icons build a texture
 * at class initialisation ({@code ItemSpriteSheet.java:822-828}), so their value is read from
 * the {@code value()} method's source and their actions from the {@code actions(Hero)} methods
 * up the hierarchy, unconditional adds only (a ternary on being equipped takes its unequipped
 * branch, as a fresh instance is). The strength requirement of a weapon or an armor is the
 * instance's at level 0, with the formula's text and citation. The classes that are not items a
 * player meets are named with their reasons, and {@code CodexCompletenessTest} enumerates the
 * game's concrete item classes against the three lists, so that a class the game adds is missing
 * loudly.
 */
final class Items {

    static final String SELECTOR = "a selector's placeholder, not an item a player meets";
    static final String OUTSIDE = "not an item a player meets: a helper outside the item packages";
    static final String BASE = "a base class, not an item a player meets";
    static final String NO_CONSTRUCTOR = "no public no-argument constructor";
    static final String NEVER_DROPPED = "never dropped, as its source says above the declaration: the seed of a plant only the wand grows";
    static final String ICONS = "its icon needs the toolkit at class initialisation (ItemSpriteSheet.Icons)";

    private static final Pattern ACTIONS_METHOD = Pattern.compile("ArrayList<String>\\s+actions\\s*\\(");
    private static final Pattern ADD = Pattern.compile("\\bactions\\.add\\s*\\(\\s*(.*?)\\s*\\)\\s*;");
    private static final Pattern REMOVE = Pattern.compile("\\bactions\\.remove\\s*\\(\\s*(\\w+)\\s*\\)\\s*;");
    private static final Pattern CLEAR = Pattern.compile("\\bactions\\.clear\\s*\\(\\s*\\)\\s*;");
    private static final Pattern OTHER_MUTATION = Pattern.compile("\\bactions\\s*(?:=[^=]|\\.(?:addAll|removeAll|removeIf|retainAll|set|sort|add\\s*\\(\\s*\\d|remove\\s*\\(\\s*\\d)\\b)");
    private static final Pattern INHERITS = Pattern.compile("=\\s*super\\.actions\\s*\\(");
    private static final Pattern FRESH = Pattern.compile("^\\s*ArrayList<String>\\s+actions\\s*=\\s*new\\s+ArrayList<");
    private static final Pattern CONTROL = Pattern.compile("^\\s*(?:if|else|for|while|do|switch|case|default|try|catch|finally|synchronized)\\b");
    private static final Pattern EQUIPPED = Pattern.compile("^(!?)\\s*isEquipped\\s*\\(\\s*hero\\s*\\)$");
    private static final Pattern LITERAL_VALUE = Pattern.compile("^return\\s+(\\d+)(?:\\s*\\*\\s*quantity)?\\s*;$");
    private static final Pattern QUANTITY = Pattern.compile("^\\s*(?:(?:public|protected|private)\\s+)?(?:int\\s+)?quantity\\s*=\\s*(\\d+)\\s*;\\s*$");
    private static final String VALUE = "public int value\\s*\\(\\s*\\)";

    /**
     * Every constructed class, or superclass of one, whose constructor, initialiser,
     * {@code actions(Hero)}, {@code value()} or {@code STRReq(int)} reads a Run, a Profile, a
     * quest, the clock or a generator, each with its reason; {@code CodexCompletenessTest}
     * enumerates the readers from bytecode and holds that this names each and nothing else, so
     * that a new read is reviewed before it can reach a table.
     */
    static final List<Map.Entry<Class<?>, String>> READS = List.of(
            Map.entry(Pickaxe.class, "actions reads Dungeon.level: on the mining level a pickaxe is neither dropped nor thrown; the context sets no level"),
            Map.entry(DriedRose.class, "actions reads Ghost.Quest.completed() and Dungeon.level; a fresh rose is unidentified and carries no ghost, so the "
                    + "quest's state changes nothing it offers, and the context sets no level"),
            Map.entry(Pasty.class, "its initialiser reads the clock through Holiday for its sprite and flavour; nothing the table carries depends on it"),
            Map.entry(UnstableSpellbook.class, "its initialiser draws the scrolls it holds (setupScrolls, Random.chances) under the Codex's generator; "
                    + "nothing the table carries depends on them"));

    /** Every item class that constructs on the generator's classpath, one supplier each. */
    static final List<Supplier<Item>> CONSTRUCTED = List.of(
            Amulet::new, Ankh::new, ArcaneResin::new, BrokenSeal::new, Dewdrop::new, EnergyCrystal::new, Gold::new,
            Honeypot::new, Honeypot.ShatteredPot::new, KingsCrown::new, LiquidMetal::new, LostBackpack::new,
            Stylus::new, TengusMask::new, Torch::new, Waterskin::new, ClericArmor::new, ClothArmor::new,
            DuelistArmor::new, HuntressArmor::new, LeatherArmor::new, MageArmor::new, MailArmor::new, PlateArmor::new,
            RogueArmor::new, ScaleArmor::new, WarriorArmor::new, AlchemistsToolkit::new, CapeOfThorns::new,
            ChaliceOfBlood::new, CloakOfShadows::new, DriedRose::new, DriedRose.Petal::new, EtherealChains::new,
            HolyTome::new, HornOfPlenty::new, LloydsBeacon::new, MasterThievesArmband::new, SandalsOfNature::new,
            SkeletonKey::new, TalismanOfForesight::new, TimekeepersHourglass::new, TimekeepersHourglass.sandBag::new,
            UnstableSpellbook::new, MagicalHolster::new, PotionBandolier::new, ScrollHolder::new, VelvetPouch::new,
            ArcaneBomb::new, Bomb::new, Bomb.ConjuredBomb::new, Bomb.DoubleBomb::new, Firebomb::new,
            FlashBangBomb::new, FrostBomb::new, HolyBomb::new, Noisemaker::new, RegrowthBomb::new, ShrapnelBomb::new,
            SmokeBomb::new, WoollyBomb::new, Berry::new, Blandfruit::new, Blandfruit.Chunks::new,
            ChargrilledMeat::new, Food::new, FrozenCarpaccio::new, MeatPie::new, MysteryMeat::new, Pasty::new,
            Pasty.FishLeftover::new, PhantomMeat::new, SmallRation::new, StewedMeat::new, SupplyRation::new,
            AlchemyPage::new, GuidePage::new, Guidebook::new, RegionLorePage.Caves::new, RegionLorePage.City::new,
            RegionLorePage.Halls::new, RegionLorePage.Prison::new, RegionLorePage.Sewers::new, CrystalKey::new,
            GoldenKey::new, IronKey::new, WornKey::new, AquaBrew::new, BlizzardBrew::new, CausticBrew::new,
            InfernalBrew::new, ShockingBrew::new, UnstableBrew::new, ElixirOfAquaticRejuvenation::new,
            ElixirOfArcaneArmor::new, ElixirOfDragonsBlood::new, ElixirOfFeatherFall::new,
            ElixirOfHoneyedHealing::new, ElixirOfIcyTouch::new, ElixirOfMight::new, ElixirOfToxicEssence::new,
            CeremonialCandle::new, CorpseDust::new, DarkGold::new, DwarfToken::new, Embers::new, EscapeCrystal::new,
            GooBlob::new, ImpStatue::new, MetalShard::new, Pickaxe::new, VaultBeacon::new, BowFragment::new,
            BrokenHilt::new, BrokenStaff::new, CloakScrap::new, SealShard::new, TornPage::new, Alchemize::new,
            BeaconOfReturning::new, CurseInfusion::new, MagicalInfusion::new, PhaseShift::new, ReclaimTrap::new,
            Recycle::new, SummonElemental::new, TelekineticGrab::new, UnstableSpell::new, WildEnergy::new,
            StoneOfAggression::new, StoneOfAugmentation::new, StoneOfBlast::new, StoneOfBlink::new,
            StoneOfClairvoyance::new, StoneOfDeepSleep::new, StoneOfDetectMagic::new, StoneOfEnchantment::new,
            StoneOfFear::new, StoneOfFlock::new, StoneOfIntuition::new, StoneOfShock::new, ChaoticCenser::new,
            CrackedSpyglass::new, DimensionalSundial::new, ExoticCrystals::new, EyeOfNewt::new, FerretTuft::new,
            MimicTooth::new, MossyClump::new, ParchmentScrap::new, PetrifiedSeed::new, RatSkull::new, SaltCube::new,
            ShardOfOblivion::new, ThirteenLeafClover::new, TrapMechanism::new, TrinketCatalyst::new, VialOfBlood::new,
            WondrousResin::new, WandOfBlastWave::new, WandOfCorrosion::new, WandOfCorruption::new,
            WandOfDisintegration::new, WandOfFireblast::new, WandOfFrost::new, WandOfLightning::new,
            WandOfLivingEarth::new, WandOfMagicMissile::new, WandOfPrismaticLight::new, WandOfRegrowth::new,
            WandOfTransfusion::new, WandOfWarding::new, SpiritBow::new, Explosive.ExplosiveCurseBomb::new,
            AssassinsBlade::new, BattleAxe::new, Crossbow::new, Cudgel::new, Dagger::new, Dirk::new, Flail::new,
            Gauntlet::new, Glaive::new, Gloves::new, Greataxe::new, Greatshield::new, Greatsword::new, HandAxe::new,
            Katana::new, Longsword::new, Mace::new, MagesStaff::new, Quarterstaff::new, Rapier::new, RoundShield::new,
            RunicBlade::new, Sai::new, Scimitar::new, Shortsword::new, Sickle::new, Spear::new, Sword::new,
            WarHammer::new, WarScythe::new, Whip::new, WornShortsword::new, Bolas::new, FishingSpear::new,
            ForceCube::new, HeavyBoomerang::new, Javelin::new, Kunai::new, Shuriken::new, ThrowingClub::new,
            ThrowingHammer::new, ThrowingKnife::new, ThrowingSpear::new, ThrowingSpike::new, ThrowingStone::new,
            Tomahawk::new, Trident::new, AdrenalineDart::new, BlindingDart::new, ChillingDart::new,
            CleansingDart::new, Dart::new, DisplacingDart::new, HealingDart::new, HolyDart::new, IncendiaryDart::new,
            ParalyticDart::new, PoisonDart::new, RotDart::new, ShockingDart::new, BlandfruitBush.Seed::new,
            Blindweed.Seed::new, Earthroot.Seed::new, Fadeleaf.Seed::new, Firebloom.Seed::new, Icecap.Seed::new,
            Mageroyal.Seed::new, Rotberry.Seed::new, Sorrowmoss.Seed::new, Starflower.Seed::new, Stormvine.Seed::new,
            Sungrass.Seed::new, Swiftthistle.Seed::new
    );

    /** The item classes whose initialiser needs the toolkit: read from source. */
    static final List<Class<? extends Item>> SOURCE_READ = List.of(
            PotionOfExperience.class, PotionOfFrost.class, PotionOfHaste.class, PotionOfHealing.class,
            PotionOfInvisibility.class, PotionOfLevitation.class, PotionOfLiquidFlame.class, PotionOfMindVision.class,
            PotionOfParalyticGas.class, PotionOfPurity.class, PotionOfStrength.class, PotionOfToxicGas.class,
            PotionOfCleansing.class, PotionOfCorrosiveGas.class, PotionOfDivineInspiration.class,
            PotionOfDragonsBreath.class, PotionOfEarthenArmor.class, PotionOfMagicalSight.class,
            PotionOfMastery.class, PotionOfShielding.class, PotionOfShroudingFog.class, PotionOfSnapFreeze.class,
            PotionOfStamina.class, PotionOfStormClouds.class, RingOfAccuracy.class, RingOfArcana.class,
            RingOfElements.class, RingOfEnergy.class, RingOfEvasion.class, RingOfForce.class, RingOfFuror.class,
            RingOfHaste.class, RingOfMight.class, RingOfSharpshooting.class, RingOfTenacity.class, RingOfWealth.class,
            ScrollOfIdentify.class, ScrollOfLullaby.class, ScrollOfMagicMapping.class, ScrollOfMirrorImage.class,
            ScrollOfRage.class, ScrollOfRecharging.class, ScrollOfRemoveCurse.class, ScrollOfRetribution.class,
            ScrollOfTeleportation.class, ScrollOfTerror.class, ScrollOfTransmutation.class, ScrollOfUpgrade.class,
            ScrollOfAntiMagic.class, ScrollOfChallenge.class, ScrollOfDivination.class, ScrollOfDread.class,
            ScrollOfEnchantment.class, ScrollOfForesight.class, ScrollOfMetamorphosis.class,
            ScrollOfMysticalEnergy.class, ScrollOfPassage.class, ScrollOfPrismaticImage.class,
            ScrollOfPsionicBlast.class, ScrollOfSirensSong.class
    );

    /** The concrete item classes that are not items a player meets, each with its reason. */
    static final List<Map.Entry<Class<? extends Item>, String>> EXCLUDED = List.of(
            Map.entry(Belongings.Backpack.class, OUTSIDE),
            Map.entry(HolyLance.HolyLanceVFX.class, OUTSIDE),
            Map.entry(GnollGeomancer.Boulder.class, OUTSIDE),
            Map.entry(Tengu.BombAbility.BombItem.class, OUTSIDE),
            Map.entry(Tengu.ShockerAbility.ShockerItem.class, OUTSIDE),
            Map.entry(VaultMirror.MirrorSword.class, OUTSIDE),
            Map.entry(Item.class, BASE),
            Map.entry(Armor.class, NO_CONSTRUCTOR),
            Map.entry(Artifact.class, BASE),
            Map.entry(Bag.class, BASE),
            Map.entry(Potion.class, BASE),
            Map.entry(ExoticPotion.class, BASE),
            Map.entry(Ring.class, BASE),
            Map.entry(SpiritBow.SpiritArrow.class, NO_CONSTRUCTOR),
            Map.entry(MeleeWeapon.class, BASE),
            Map.entry(Plant.Seed.class, BASE),
            Map.entry(ScorpioSprite.ScorpioShot.class, NO_CONSTRUCTOR),
            Map.entry(TenguSprite.TenguShuriken.class, OUTSIDE),
            Map.entry(WndBag.Placeholder.class, NO_CONSTRUCTOR),
            Map.entry(Potion.PlaceHolder.class, SELECTOR),
            Map.entry(Scroll.PlaceHolder.class, SELECTOR),
            Map.entry(Runestone.PlaceHolder.class, SELECTOR),
            Map.entry(Trinket.PlaceHolder.class, SELECTOR),
            Map.entry(Wand.PlaceHolder.class, SELECTOR),
            Map.entry(MissileWeapon.PlaceHolder.class, SELECTOR),
            Map.entry(Plant.Seed.PlaceHolder.class, SELECTOR),
            Map.entry(MysteryMeat.PlaceHolder.class, SELECTOR),
            Map.entry(TrinketCatalyst.RandomTrinket.class, SELECTOR),
            Map.entry(WandOfRegrowth.Dewcatcher.Seed.class, NEVER_DROPPED),
            Map.entry(WandOfRegrowth.Seedpod.Seed.class, NEVER_DROPPED)
    );

    private Items() {
    }

    /** Every item's entry, by class name. */
    static List<Codex.ItemEntry> entries(Path root) {
        List<Codex.ItemEntry> entries = new ArrayList<>();
        Hero hero = GameContext.under(1, 0, Hero::new);
        for (Supplier<Item> make : CONSTRUCTED) {
            entries.add(constructed(root, make, hero));
        }
        for (Class<? extends Item> type : SOURCE_READ) {
            entries.add(sourceRead(root, type));
        }
        entries.sort(Comparator.comparing(Codex.ItemEntry::className));
        return entries;
    }

    /** A constructed item's entry: the fresh instance's value and actions, its strength, its name and its category. */
    static Codex.ItemEntry constructed(Path root, Supplier<Item> make, Hero hero) {
        Item item = GameContext.under(1, 0, make);
        Class<?> type = item.getClass();
        Sources.Body body = Sources.body(root, type);
        Names.Named name = Names.of(root, type);
        int value = GameContext.under(1, 0, item::value);
        List<String> actions = List.copyOf(GameContext.under(1, 0, () -> item.actions(hero)));
        return new Codex.ItemEntry(Sources.name(type), name.value(), customName(root, type), name.citation(), category(type), item.quantity(), value, "",
                strength(root, item), actions, true, "", body.citation(body.declaration(type.getSimpleName())));
    }

    /** Whether the class or a superclass below {@code Item} overrides {@code name()}, so the screen may show a name that is not the bundle's. */
    static boolean customName(Path root, Class<?> type) {
        Sources.Declared name = Sources.declared(root, type, Item.class, "public String name\\s*\\(\\s*\\)");
        return name != null && name.owner() != Item.class;
    }

    /** The quantity a fresh instance holds, from the nearest initialiser assigning the field up to {@code Item}'s own declaration. */
    static int quantityOf(Path root, Class<?> type) {
        for (Class<?> c = type; c != null && Item.class.isAssignableFrom(c); c = c.getSuperclass()) {
            int line = Sources.body(root, c).firstMember(QUANTITY);
            if (line >= 0) {
                Matcher m = QUANTITY.matcher(Sources.body(root, c).lines().get(line));
                m.matches();
                return Integer.parseInt(m.group(1));
            }
        }
        throw new IllegalStateException(type.getName() + " has no quantity declaration up to Item");
    }

    /** A source-read item's entry: the value method's text, the literal when it is one, the actions the hierarchy adds. */
    static Codex.ItemEntry sourceRead(Path root, Class<? extends Item> type) {
        Sources.Body body = Sources.body(root, type);
        Names.Named name = Names.of(root, type);
        Sources.Declared valueMethod = Sources.declared(root, type, Item.class, VALUE);
        if (valueMethod == null) {
            throw new IllegalStateException(type.getName() + " declares no value() up to Item");
        }
        List<String> returns = Sources.returns(valueMethod.block());
        int value = -1;
        if (returns.size() == 1) {
            Matcher literal = LITERAL_VALUE.matcher(returns.get(0));
            if (literal.matches()) {
                value = Integer.parseInt(literal.group(1));
            }
        }
        return new Codex.ItemEntry(Sources.name(type), name.value(), customName(root, type), name.citation(), category(type), quantityOf(root, type), value,
                valueText(root, type, valueMethod), Codex.Strength.none(), actions(root, type), false, ICONS,
                body.citation(body.declaration(type.getSimpleName())));
    }

    /**
     * The value method's text, and when it defers to {@code super.value()} (as every identifiable
     * potion and scroll does, by whether it is known), the superclass's text after it, up the
     * hierarchy until one does not defer; when it defers to the regular class's instance (as the
     * exotics do, through {@code exoToReg}), that class's text, read the same way.
     */
    static String valueText(Path root, Class<?> type, Sources.Declared valueMethod) {
        StringBuilder text = new StringBuilder(text(valueMethod.block()));
        Sources.Declared current = valueMethod;
        while (text(current.block()).contains("super.value()")) {
            Class<?> parent = current.owner().getSuperclass();
            if (parent == null || !Item.class.isAssignableFrom(parent)) {
                break;
            }
            Sources.Declared above = Sources.declared(root, parent, Item.class, VALUE);
            if (above == null) {
                throw new IllegalStateException(current.owner().getName() + ".value defers to a superclass that declares none");
            }
            text.append(" where super.value(): ").append(text(above.block()));
            current = above;
        }
        if (text(valueMethod.block()).contains("exoToReg.get(getClass())")) {
            Class<?> regular = ExoticPotion.class.isAssignableFrom(type) ? ExoticPotion.exoToReg.get(type)
                    : ExoticScroll.class.isAssignableFrom(type) ? ExoticScroll.exoToReg.get(type) : null;
            if (regular == null) {
                throw new IllegalStateException(type.getName() + ".value defers to a regular class the exotic maps do not give");
            }
            Sources.Declared regularValue = Sources.declared(root, regular, Item.class, VALUE);
            if (regularValue == null) {
                throw new IllegalStateException(regular.getName() + " declares no value() up to Item");
            }
            text.append(" where the regular's value(), ").append(Sources.name(regular)).append(": ").append(valueText(root, regular, regularValue));
        }
        return text.toString();
    }

    /** The one generator category whose class list holds {@code type}, or an empty name; two fail. */
    static String category(Class<?> type) {
        String found = "";
        for (Generator.Category category : Generator.Category.values()) {
            if (category.classes == null) {
                continue;
            }
            for (Class<?> listed : category.classes) {
                if (listed == type) {
                    if (!found.isEmpty()) {
                        throw new IllegalStateException(type.getName() + " is in two categories: " + found + " and " + category.name());
                    }
                    found = category.name();
                }
            }
        }
        return found;
    }

    /**
     * A weapon's or an armor's strength requirement at level 0, the tier the instance carries
     * (0 for a weapon without a tier field, whose own formula names it), the text of its own
     * {@code STRReq(int lvl)} and of the family's static formula, cited to the formula.
     */
    static Codex.Strength strength(Path root, Item item) {
        if (!(item instanceof Weapon) && !(item instanceof Armor)) {
            return Codex.Strength.none();
        }
        int tier = item instanceof MeleeWeapon melee ? melee.tier : item instanceof MissileWeapon missile ? missile.tier
                : item instanceof Armor armor ? armor.tier : 0;
        int atLevel0 = GameContext.under(1, 0, () -> item instanceof Weapon weapon ? weapon.STRReq(0) : ((Armor) item).STRReq(0));
        Class<?> family = item instanceof Weapon ? Weapon.class : Armor.class;
        Sources.Declared own = Sources.declared(root, item.getClass(), family, "public int STRReq\\s*\\(\\s*int lvl\\s*\\)");
        if (own == null) {
            throw new IllegalStateException(item.getClass().getName() + " declares no STRReq(int lvl) up to " + family.getSimpleName());
        }
        Sources.Body familyBody = Sources.body(root, family);
        int formula = familyBody.find("static int STRReq\\s*\\(\\s*int tier\\s*,\\s*int lvl\\s*\\)");
        if (formula < 0) {
            throw new IllegalStateException(family.getName() + " declares no static STRReq(int tier, int lvl)");
        }
        String expression = text(own.block()) + " where STRReq(tier, lvl): " + text(familyBody.block(formula));
        return new Codex.Strength(true, tier, atLevel0, expression, familyBody.citation(formula));
    }

    /**
     * The actions a fresh instance of {@code type} offers, read from every {@code actions(Hero)}
     * the hierarchy declares from {@code Item} down by {@link #readActions}. The constants are
     * resolved to their values in the class that declares them.
     */
    static List<String> actions(Path root, Class<?> type) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(0, c);
        }
        List<String> actions = new ArrayList<>();
        for (Class<?> c : chain) {
            Sources.Body body = Sources.body(root, c);
            int line = body.find(ACTIONS_METHOD.pattern());
            if (line < 0) {
                continue;
            }
            actions = readActions(body.block(line), actions, name -> constant(root, chain, name));
        }
        return List.copyOf(actions);
    }

    /**
     * What one {@code actions(Hero)} method offers a fresh, unequipped instance, given what the
     * superclass's offers: the list starts as the superclass's when the body assigns
     * {@code super.actions(hero)} at its own level and empty otherwise; an {@code add}, a
     * {@code remove} or a {@code clear} at the body's own level counts, one on or under a
     * control line (an {@code if} with or without braces, a loop, a switch) does not, since a
     * fresh instance takes none of those branches the reader can name; a ternary on
     * {@code isEquipped(hero)} takes its unequipped branch. Braces are counted outside comments
     * and literals, the opening brace wherever it is. Any other shape (a nested or other ternary,
     * an {@code addAll}, a reassignment, a control line the reader cannot see past) fails
     * naming the line, so that a shape a later tag writes is read on purpose or not at all.
     */
    static List<String> readActions(Sources.Body method, List<String> inherited, java.util.function.UnaryOperator<String> resolve) {
        List<String> actions = new ArrayList<>();
        boolean inherits = false;
        boolean opened = false;
        int depth = 0;
        boolean conditional = false;
        for (int i = method.from(); i < method.to(); i++) {
            String text = Sources.stripComment(method.lines().get(i));
            String trimmed = text.trim();
            int change = Sources.braces(text);
            boolean bodyLevel = opened && depth == 1;
            boolean control = CONTROL.matcher(text).find();
            if (bodyLevel && !control && !conditional) {
                Matcher add = ADD.matcher(text);
                Matcher remove = REMOVE.matcher(text);
                if (OTHER_MUTATION.matcher(text).find() && !INHERITS.matcher(text).find() && !FRESH.matcher(text).find()) {
                    throw new IllegalStateException(method.path() + ":" + (i + 1) + ": a change to the actions the reader does not know: " + trimmed);
                }
                if (INHERITS.matcher(text).find()) {
                    actions.addAll(inherited);
                    inherits = true;
                } else if (add.find()) {
                    actions.add(resolve.apply(branch(add.group(1), method.path(), i)));
                } else if (remove.find()) {
                    actions.remove(resolve.apply(remove.group(1)));
                } else if (CLEAR.matcher(text).find()) {
                    actions.clear();
                }
            } else if (bodyLevel && (control || conditional) && OTHER_MUTATION.matcher(text).find()) {
                throw new IllegalStateException(method.path() + ":" + (i + 1) + ": a change to the actions under a condition the reader does not know: " + trimmed);
            }
            // A control line that closes nothing governs the next statement, which is not offered.
            boolean open = !trimmed.endsWith(";") && !trimmed.endsWith("{") && !trimmed.endsWith("}");
            conditional = bodyLevel && control && change == 0 && open || conditional && open && change == 0;
            depth += change;
            opened |= change > 0;
        }
        if (!inherits && !inherited.isEmpty() && method.lines().subList(method.from(), method.to()).stream().anyMatch(l -> l.contains("super.actions"))) {
            throw new IllegalStateException(method.path() + ":" + (method.from() + 1) + ": super.actions is called somewhere the reader cannot see");
        }
        return actions;
    }

    /** The constant an add's argument names: itself, or the unequipped branch of a ternary on being equipped. */
    private static String branch(String argument, String path, int line) {
        int question = argument.indexOf('?');
        if (question < 0) {
            return argument.trim();
        }
        int colon = argument.indexOf(':');
        if (argument.indexOf('?', question + 1) >= 0 || argument.indexOf(':', colon + 1) >= 0 || colon < question) {
            throw new IllegalStateException(path + ":" + (line + 1) + ": a ternary the reader does not know: " + argument);
        }
        Matcher equipped = EQUIPPED.matcher(argument.substring(0, question).trim());
        if (!equipped.matches()) {
            throw new IllegalStateException(path + ":" + (line + 1) + ": a condition the reader does not know: " + argument);
        }
        boolean negated = !equipped.group(1).isEmpty();
        return (negated ? argument.substring(question + 1, colon) : argument.substring(colon + 1)).trim();
    }

    /** The value of the {@code AC_*} constant {@code name}, declared as a string literal in one of {@code chain}'s classes. */
    private static String constant(Path root, List<Class<?>> chain, String name) {
        if (!name.matches("[A-Z_][A-Z_0-9]*")) {
            throw new IllegalStateException("an action that is not a constant: " + name);
        }
        Pattern declaration = Pattern.compile("^\\s*public\\s+static\\s+(?:final\\s+)?String\\s+" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"\\s*;\\s*$");
        for (Class<?> c : chain) {
            int line = Sources.body(root, c).firstMember(declaration);
            if (line >= 0) {
                Matcher m = declaration.matcher(Sources.body(root, c).lines().get(line));
                m.matches();
                return m.group(1);
            }
        }
        throw new IllegalStateException(name + " is not a string constant of " + chain.get(chain.size() - 1).getName() + " or a superclass");
    }

    /** A method block's body as one line: its statements, comments stripped, blank lines dropped. */
    private static String text(Sources.Body block) {
        return Sources.text(block);
    }
}
