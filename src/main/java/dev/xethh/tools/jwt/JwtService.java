package dev.xethh.tools.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import dev.xethh.libs.toolkits.commons.encryption.RSAFormatting;
import dev.xethh.libs.toolkits.commons.encryption.RsaEncryption;
import dev.xethh.tools.config.CustConfig;
import me.xethh.utils.dateUtils.D;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class JwtService {
    @Autowired
    CustConfig custConfig;

    public Algorithm algorithm() throws IOException {
        var pubIn = new FileInputStream(custConfig.keyPairDir()+"/pub.pem");
        var rsaPublicKey = RsaEncryption.getPublicKey(pubIn.readAllBytes());
        var priIn = new FileInputStream(custConfig.keyPairDir()+"/pri.pem");
        var rsaPrivateKey = RsaEncryption.getPrivateKey(priIn.readAllBytes());
        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) rsaPublicKey, (RSAPrivateKey) rsaPrivateKey);
        return algorithm;
    }

    static final String ISSUER = "*.xethh.dev";
    public static final String SCOPE = "file-upload";
    static final String CLAIM_SCOPE = "scope";
    static final String CLAIM_USER_SCOPE = "user-scope";
    public String getToken(String user){
        try{
            var algorithm = algorithm();
            var now = D.dt().now();
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withExpiresAt(now.addMonths(1).asDate())
                    .withClaim(CLAIM_SCOPE, List.of(SCOPE))
                    .withClaim(CLAIM_USER_SCOPE, user)
                    .sign(algorithm);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    public Optional<DecodedJWT> verify(String token, String scope, String userScope) {
        DecodedJWT decodedJWT;
        try {
            JWTVerifier verifier = JWT.require(algorithm())
                    .withIssuer(ISSUER)
                    .withClaim(CLAIM_USER_SCOPE, userScope)
                    .withArrayClaim(CLAIM_SCOPE, scope)
                    .build();

            decodedJWT = verifier.verify(token);
            return Optional.of(decodedJWT);
        } catch (JWTVerificationException | IOException exception){
            throw new RuntimeException(exception);
        }
    }
}
