package com.ebremer.halcyon.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author erich
 */
@RestController
public class Test {
    
    
    @GetMapping("/skunkworks/hello")
    public String helloWorld() {
        return "Hello, World!";
    }    
}
