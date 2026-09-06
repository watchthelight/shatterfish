package org.shatterfish.api;

/**
 * The hero's subclass ({@code core/.../actors/hero/HeroSubClass.java:33-51}), which names the hero
 * in the hero window once chosen ({@code core/.../actors/hero/Hero.java:412-414};
 * {@code core/.../windows/WndHero.java:160-162}); {@link #NONE} before the choice.
 */
public enum HeroSubclass {
    NONE,
    BERSERKER, GLADIATOR,
    BATTLEMAGE, WARLOCK,
    ASSASSIN, FREERUNNER,
    SNIPER, WARDEN,
    CHAMPION, MONK,
    PRIEST, PALADIN
}
