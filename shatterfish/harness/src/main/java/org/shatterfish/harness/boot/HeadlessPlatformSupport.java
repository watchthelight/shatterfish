package org.shatterfish.harness.boot;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.watabou.utils.PlatformSupport;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The platform the game runs on when there is no platform: no display, no network, no vibration,
 * and text rendered through FreeType exactly as the desktop build renders it.
 *
 * <p>{@code Game.platform} is dereferenced without a null check by every scene
 * ({@code PixelScene.create()} reads the safe insets and sets up the font generators,
 * {@code core/.../scenes/PixelScene.java:127}, {@code :173}), so a headless Run needs one. The
 * desktop implementation lives in the {@code desktop} module behind LWJGL, which the harness must
 * not depend on, so the font half of it is carried here. It is a copy in behaviour of
 * {@code desktop/.../DesktopPlatformSupport.java:107-181} at the pinned tag, kept identical on
 * purpose: which glyphs a string splits into decides how text lays out, and text layout is part of
 * what the Overlay draws.
 *
 * <p>Fonts are the one place the scene touches libGDX's own graphics classes rather than the
 * game's. FreeType rasterizes glyphs on the CPU into a {@link PixmapPacker}, whose pages become
 * libGDX textures; those go through {@code Gdx.gl} like everything else and the no-op binding
 * accepts them. The native library comes from {@code gdx-freetype-platform}, declared in
 * {@code shatterfish/harness/build.gradle}.
 */
public final class HeadlessPlatformSupport extends PlatformSupport {

    // Custom pixel font for Latin and Cyrillic languages; Droid Sans as the fallback for Asian
    // scripts. Both ship in core/src/main/assets/fonts.
    private static FreeTypeFontGenerator basicFontGenerator;
    private static FreeTypeFontGenerator asianFontGenerator;

    private static final Matcher ASIAN = Pattern.compile("\\p{InHangul_Syllables}|"
            + "\\p{InCJK_Unified_Ideographs}|\\p{InCJK_Symbols_and_Punctuation}|\\p{InHalfwidth_and_Fullwidth_Forms}|"
            + "\\p{InHiragana}|\\p{InKatakana}").matcher("");

    // Splits on newline (for layout), Chinese/Japanese (for font choice), and '_'/'**' (for
    // highlighting).
    private static final Pattern REGULAR_SPLITTER = Pattern.compile(
            "(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|"
                    + "(?<=\\p{InHiragana})|(?=\\p{InHiragana})|"
                    + "(?<=\\p{InKatakana})|(?=\\p{InKatakana})|"
                    + "(?<=\\p{InCJK_Unified_Ideographs})|(?=\\p{InCJK_Unified_Ideographs})|"
                    + "(?<=\\p{InCJK_Symbols_and_Punctuation})|(?=\\p{InCJK_Symbols_and_Punctuation})");

    // Additionally splits on spaces, so that each word can be laid out individually.
    private static final Pattern REGULAR_SPLITTER_MULTILINE = Pattern.compile(
            "(?<= )|(?= )|(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|"
                    + "(?<=\\p{InHiragana})|(?=\\p{InHiragana})|"
                    + "(?<=\\p{InKatakana})|(?=\\p{InKatakana})|"
                    + "(?<=\\p{InCJK_Unified_Ideographs})|(?=\\p{InCJK_Unified_Ideographs})|"
                    + "(?<=\\p{InCJK_Symbols_and_Punctuation})|(?=\\p{InCJK_Symbols_and_Punctuation})");

    /** There is no display to size. */
    @Override
    public void updateDisplaySize() {
    }

    /** There is no display to go full screen on. */
    @Override
    public boolean supportsFullScreen() {
        return false;
    }

    /** There is no system UI to show or hide. */
    @Override
    public void updateSystemUI() {
    }

    /** Never: the game uses this to decide whether to fetch news, which a Run must not. */
    @Override
    public boolean connectedToUnmeteredNetwork() {
        return false;
    }

    @Override
    public boolean supportsVibration() {
        return false;
    }

    @Override
    public void setupFontGenerators(int pageSize, boolean systemfont) {
        // Don't bother doing anything if nothing has changed.
        if (fonts != null && this.pageSize == pageSize && this.systemfont == systemfont) {
            return;
        }
        this.pageSize = pageSize;
        this.systemfont = systemfont;

        resetGenerators(false);
        fonts = new HashMap<>();

        if (systemfont) {
            basicFontGenerator = asianFontGenerator =
                    new FreeTypeFontGenerator(Gdx.files.internal("fonts/droid_sans.ttf"));
        } else {
            basicFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
            asianFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/droid_sans.ttf"));
        }

        fonts.put(basicFontGenerator, new HashMap<>());
        fonts.put(asianFontGenerator, new HashMap<>());

        packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 1, false);
    }

    @Override
    protected FreeTypeFontGenerator getGeneratorForString(String input) {
        if (ASIAN.reset(input).find()) {
            return asianFontGenerator;
        } else {
            return basicFontGenerator;
        }
    }

    @Override
    public String[] splitforTextBlock(String text, boolean multiline) {
        if (multiline) {
            return REGULAR_SPLITTER_MULTILINE.split(text);
        } else {
            return REGULAR_SPLITTER.split(text);
        }
    }
}
