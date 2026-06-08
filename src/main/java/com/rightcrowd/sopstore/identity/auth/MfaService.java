package com.rightcrowd.sopstore.identity.auth;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

/** TOTP enrollment & verification. WebAuthn lives in {@code WebAuthnService}. */
@Service
public class MfaService {

  private final CodeVerifier verifier =
      new DefaultCodeVerifier(
          new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6), new SystemTimeProvider());
  private final DefaultSecretGenerator secrets = new DefaultSecretGenerator(64);

  /** Generates a new TOTP shared secret. */
  public String newSecret() {
    return secrets.generate();
  }

  /** Renders an enrollment QR code PNG for the given email and secret. */
  public byte[] qrPng(String email, String secret) throws Exception {
    QrData data =
        new QrData.Builder()
            .label(email)
            .secret(secret)
            .issuer("sopstore")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();
    return new ZxingPngQrGenerator().generate(data);
  }

  /** Verifies a TOTP code against the given secret. */
  public boolean verify(String secret, String code) {
    return verifier.isValidCode(secret, code);
  }
}
