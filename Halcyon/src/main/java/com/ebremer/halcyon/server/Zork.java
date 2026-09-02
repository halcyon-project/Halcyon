package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.HalcyonSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */

@RestController
public class Zork {
    private static final Logger logger = LoggerFactory.getLogger(Zork.class);
        
    @GetMapping("/skunkworks/yay")
    public String yay(HttpServletRequest request, HttpServletResponse response) {
//        response.setHeader("Alt-Svc", "h3=\":8888\"; ma=86400; persist=1");
        HalcyonSession ja;
        return "YAY!!!! ";
    }
    
    @GetMapping("/skunkworks/ram")
    public String RAM(HttpServletRequest request, HttpServletResponse response) {
  //      response.setHeader("Alt-Svc", "h3=\":8888\"; ma=86400; persist=1");
         response.getHeaderNames().forEach(h->{
             logger.debug("RAM : {}", h);
         });
        return "RAM!!!! "+response.getStatus()+"   "+request.getProtocol();
    }
    
}
