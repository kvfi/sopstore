package com.rightcrowd.sopstore.procedure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the configurable RUN_SCRIPT bundle naming token engine. */
class ScriptBundleNamingTest {

  private static final ScriptBundleConfig DEFAULTS = ScriptBundleConfig.DEFAULTS;

  @Test
  void defaultPatternBuildsCodeNameVersionExt() {
    assertThat(ScriptBundleNaming.path(DEFAULTS, "k3", "Load Dataset", 4, "sql"))
        .isEqualTo("scripts/k3_load-dataset_v4.sql");
  }

  @Test
  void emptyCodeDropsLeadingSeparator() {
    assertThat(ScriptBundleNaming.path(DEFAULTS, "", "deploy", 2, "bash"))
        .isEqualTo("scripts/deploy_v2.sh");
  }

  @Test
  void unpinnedVersionDropsTrailingSeparator() {
    assertThat(ScriptBundleNaming.path(DEFAULTS, "a7", "cleanup", 0, "python"))
        .isEqualTo("scripts/a7_cleanup.py");
  }

  @Test
  void allTokensEmptyFallsBackToScriptStem() {
    assertThat(ScriptBundleNaming.path(DEFAULTS, "", "", 0, ""))
        .isEqualTo("scripts/script.txt");
  }

  @Test
  void unknownLanguageDefaultsToTxtExtension() {
    assertThat(ScriptBundleNaming.path(DEFAULTS, "z1", "thing", 1, "brainfuck"))
        .isEqualTo("scripts/z1_thing_v1.txt");
  }

  @Test
  void customFolderIsNormalisedWithTrailingSlashAndNoLeadingSlash() {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig("/bin", "{name}.{ext}", DEFAULTS.bundleName(), "");
    assertThat(ScriptBundleNaming.path(cfg, "k3", "run", 9, "js")).isEqualTo("bin/run.js");
  }

  @Test
  void blankFolderPutsScriptsAtBundleRoot() {
    ScriptBundleConfig cfg = new ScriptBundleConfig("", "{name}_{version}.{ext}", "b.zip", "");
    assertThat(ScriptBundleNaming.path(cfg, "k3", "run", 3, "ts")).isEqualTo("run_v3.ts");
  }

  @Test
  void customPatternIsHonoured() {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig("scripts/", "{version}-{name}.{ext}", "b.zip", "");
    assertThat(ScriptBundleNaming.path(cfg, "k3", "Backup Job", 5, "ps1"))
        .isEqualTo("scripts/v5-backup-job.ps1");
  }

  @Test
  void blankLinkBaseUrlKeepsHrefRelative() {
    String href = ScriptBundleNaming.scriptHref(DEFAULTS, "k3", "deploy", 4, "sql");
    assertThat(href).isEqualTo("scripts/k3_deploy_v4.sql");
  }

  @Test
  void linkBaseUrlPrefixesHrefAndTrimsTrailingSlash() {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig(
            DEFAULTS.folder(), DEFAULTS.filenamePattern(), DEFAULTS.bundleName(), "https://x.test/sop/");
    String href = ScriptBundleNaming.scriptHref(cfg, "k3", "deploy", 4, "sql");
    assertThat(href).isEqualTo("https://x.test/sop/scripts/k3_deploy_v4.sql");
  }

  @Test
  void bundleFileNameExpandsDocumentToken() {
    assertThat(ScriptBundleNaming.bundleFileName(DEFAULTS, "SOP-001"))
        .isEqualTo("SOP-001-bundle.zip");
  }

  @Test
  void bundleFileNameSanitisesUnsafeCharacters() {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig(DEFAULTS.folder(), DEFAULTS.filenamePattern(), "{document}.zip", "");
    assertThat(ScriptBundleNaming.bundleFileName(cfg, "A/B C")).isEqualTo("A_B_C.zip");
  }

  @Test
  void bundleFileNameFallsBackWhenBlank() {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig(DEFAULTS.folder(), DEFAULTS.filenamePattern(), "{document}", "");
    assertThat(ScriptBundleNaming.bundleFileName(cfg, "")).isEqualTo("bundle.zip");
  }
}
