package com.rightcrowd.sopstore.architecture;

import com.rightcrowd.sopstore.SopStoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies module boundaries declared via {@code @ApplicationModule} are respected. This test fails
 * the build if a class in module A imports from module B's internal package without going through a
 * {@code @NamedInterface}.
 */
class ModulithVerificationTest {

  private final ApplicationModules modules = ApplicationModules.of(SopStoreApplication.class);

  @Test
  void verifiesModuleBoundaries() {
    modules.verify();
  }

  @Test
  void generatesDocumentation() {
    new Documenter(modules).writeDocumentation().writeIndividualModulesAsPlantUml();
  }
}
