package org.shatterfish.overlay;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.toolkitfixture.AwtPanel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native-UI rule as a check rather than a habit (story 5.2, non-negotiable 6): the Overlay draws
 * with the game's own toolkit, so nothing in {@code overlay} imports Swing, AWT, JavaFX or a web view.
 */
class OverlayToolkitTest {

    /** The foreign toolkits: Swing, AWT, JavaFX, SWT, and the embeddable browsers. */
    static final String[] FOREIGN = {
            "java.awt..", "javax.swing..", "javafx..", "com.sun.javafx..", "org.eclipse.swt..",
            "org.cef..", "me.friwi.jcefmaven..", "com.teamdev.jxbrowser..", "org.openjfx.."
    };

    static final ArchRule NATIVE_UI_ONLY = noClasses()
            .that().resideInAPackage("org.shatterfish.overlay..")
            .should().dependOnClassesThat().resideInAnyPackage(FOREIGN)
            .because("the Overlay uses the game's own toolkit: nine-patch frames, the game's text renderer and"
                    + " its buttons, never Swing, AWT, JavaFX or a web view (non-negotiable 6)");

    private static JavaClasses overlay() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.shatterfish.overlay");
    }

    @Test
    @DisplayName("nothing in the Overlay imports Swing, AWT, JavaFX or a web view")
    void native_ui_only() {
        NATIVE_UI_ONLY.check(overlay());
    }

    @Test
    @DisplayName("the rule bites: a class in the Overlay's package that uses AWT is caught")
    void the_rule_bites() {
        JavaClasses fixture = new ClassFileImporter().importClasses(AwtPanel.class);
        assertTrue(NATIVE_UI_ONLY.evaluate(fixture).hasViolation(), "an AWT import in org.shatterfish.overlay.. is a violation");
    }
}
