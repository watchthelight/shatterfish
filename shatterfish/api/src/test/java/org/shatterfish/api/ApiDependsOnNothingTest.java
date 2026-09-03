package org.shatterfish.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** The api module is DTOs only: nothing outside the JDK and itself. */
@AnalyzeClasses(packages = "org.shatterfish.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiDependsOnNothingTest {

    @ArchTest
    static final ArchRule api_uses_only_jdk_and_itself = classes()
            .that().resideInAPackage("org.shatterfish.api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("org.shatterfish.api..", "java..")
            .allowEmptyShould(true);
}
