package com.ebremer.halycon.shiro;

import io.jsonwebtoken.JwtException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.filter.AccessControlFilter;
import io.jsonwebtoken.Jwts;

 
public class JWTVerifyingFilter extends AccessControlFilter {
    
    private boolean validateJwt(String jwt) {
    try {
        Jwts.parserBuilder()
                //.setSigningKey(publicKey)
                .build()
                .parseClaimsJws(jwt);
        return true;
    } catch (JwtException e) {
        return false;
    }
}
 
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse arg1, Object arg2) throws Exception {
        boolean accessAllowed = false;
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String jwt = httpRequest.getHeader("Authorization");
        if (jwt == null || !jwt.startsWith("Bearer ")) {
            return accessAllowed;
        }
        jwt = jwt.substring(jwt.indexOf(" "));
        //String username = 
        
        
        String subjectName = (String) SecurityUtils.getSubject().getPrincipal();
        System.out.println("subjectName = "+subjectName);
       // if (username.equals(subjectName)) {
         //   accessAllowed = true;
        //}
        return accessAllowed;
    }
 
    @Override
    protected boolean onAccessDenied(ServletRequest arg0, ServletResponse arg1) throws Exception {
        HttpServletResponse response = (HttpServletResponse) arg1;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
 
}