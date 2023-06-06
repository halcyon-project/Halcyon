package com.ebremer.halcyon.fuseki.shiro;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;

public class JwtVerifier {
    private final PublicKey publicKey;

    public JwtVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Claims verify(String token) {
        try {
            JwtParser p = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(86400)
                .setSigningKey(publicKey)
                .build();
                Jws<Claims> claimx = p.parseClaimsJws(token);
            return claimx.getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            System.out.println("ARRRGGGG JWT expired!!!!");
        }  catch (io.jsonwebtoken.security.SignatureException ex) {
            System.out.println(ex.getMessage());
        }
        return new DefaultClaims();
    }
    
    public static void main(String[] args) {
        String token ="eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ6RkN0a1BiYW14S093RDBDa3M4SUJsSFZZelc3dVFTWkhpRWVTeGRYTENJIn0.eyJleHAiOjE2ODYwMDE0ODgsImlhdCI6MTY4NTk5NDI4OCwianRpIjoiMDE5ODk1ZjMtMGU2Yy00ZTI0LTgzYjAtOWYxYTQ1MmQ4MTlkIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4ODg4L2F1dGgvcmVhbG1zL0hhbGN5b24iLCJzdWIiOiJmNDg0NTlmMy0yMTY1LTQ3ZjEtYjA3MC1lZmVhMjljZTgxNDAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJhY2NvdW50Iiwic2Vzc2lvbl9zdGF0ZSI6IjE1ZDA5MzIxLWZlZmUtNGNmYi05MzdjLWE2MzM5MWY5MTBlZCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiZGVmYXVsdC1yb2xlcy1oYWxjeW9uIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsInNpZCI6IjE1ZDA5MzIxLWZlZmUtNGNmYi05MzdjLWE2MzM5MWY5MTBlZCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiZ3JvdXBzIjpbImRlZmF1bHQtcm9sZXMtaGFsY3lvbiIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iXSwiSGFsY3lvbkdyb3VwcyI6WyIvRXZlcnlvbmUiLCIvYWRtaW4iXSwicHJlZmVycmVkX3VzZXJuYW1lIjoiYWRtaW4iLCJnaXZlbl9uYW1lIjoiIiwiZmFtaWx5X25hbWUiOiIifQ.i_djD-ki1P_-G3bucE1y8qlvDGZjppbuPcN_QMjD9jU1VBzclBUJrWbQppNjCfSg0profLfqEtMCw8IQ42h54WPxPePlJOe_U9vmnoRJtQ2bWB4yDVLeHv58T0UpjorOWDUBahVEpeFEy-X9Bkd1hsO1joNS-MHNbT1QRzzQX56Im1i2FuCq_atcvI1yIjaZzW7fpCZy6lorfCg2ij795MrTlgDr6oas3whbDwzZ_irw_EOKy7onGO-feXWqOTOENpfJwC_78I91v8obxeLsnZHvvWg0ZEPxDlPNkfs-KwZAa-V8dEuU2HehJHkKfMc81HFyH54xG6fKbAFl1mF0Zw";
        JwtVerifier v = new JwtVerifier(KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getPublicKey());
        v.verify(token);
    }
}
