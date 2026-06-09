package com.rightcrowd.sopstore.procedure.internal;

/**
 * The effective, tenant-configurable settings that drive how a procedure's RUN_SCRIPT scripts are
 * named and linked in the exported SOP bundle and PDF. Resolved from {@code script_bundle_settings}
 * (one row per tenant) or, when a tenant has not customised them, from {@link #DEFAULTS}.
 *
 * @param folder the folder script files live in inside the bundle zip, e.g. {@code scripts/}
 * @param filenamePattern token template for each script's filename; tokens {@code {code} {name}
 *     {version} {ext}}
 * @param bundleName token template for the downloaded zip's filename; token {@code {document}}
 * @param linkBaseUrl optional absolute prefix for the PDF's script hyperlinks; blank means the link
 *     stays relative to the bundle (resolves only inside the unzipped bundle)
 */
record ScriptBundleConfig(
    String folder, String filenamePattern, String bundleName, String linkBaseUrl) {

  /** The application defaults, applied when a tenant has not saved its own settings. */
  static final ScriptBundleConfig DEFAULTS =
      new ScriptBundleConfig(
          "scripts/", "{code}_{name}_{version}.{ext}", "{document}-bundle.zip", "");
}
