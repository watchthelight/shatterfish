package org.shatterfish.harness.hooks;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.effects.BadgeBanner;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.utils.Random;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hook row 8, the badge banner's site (issue #245): the shine a banner adds to its badge is drawn by the render
 * thread, at the frame the banner reaches its static state, and that frame is the wall clock's. Drawn from the
 * Run's stream it moved every roll of the turn it landed in, so the same Run played with frames of a different
 * length (the desktop's, the headless driver's) parted at the first fight after a badge. The shine is for the eye
 * and takes nothing from the stack.
 */
class BadgeBannerStreamTest {

    private static final long SEED = 0x245L;

    @BeforeEach
    void ownAStack() {
        // Textures and a camera, as every sprite needs (the Speck reads its texture).
        HeadlessBoot.ensure();
        Random.pushGenerator(SEED);
    }

    @AfterEach
    void releaseIt() {
        Random.popGenerator();
    }

    @Test
    @DisplayName("the shine of a badge banner leaves the Run's stream where it was")
    void the_shine_takes_nothing_from_the_stack() {
        Random.pushGenerator(SEED);
        float first = Random.Float();
        float second = Random.Float();
        Random.popGenerator();

        Group parent = new Group();
        Image image = new Image();
        parent.add(image);
        // The badge whose highlight position is hardcoded, so no texture is read.
        BadgeBanner.highlight(image, Badges.Badge.MASTERY_COMBO.image);

        assertEquals(first, Random.Float(), "the first roll after the shine is the stream's first");
        assertEquals(second, Random.Float(), "and the second its second");
    }
}
