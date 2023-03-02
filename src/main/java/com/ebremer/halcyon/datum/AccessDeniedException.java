/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.datum;

/**
 *
 * @author erich
 */
public class AccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 2605607948156624590L;

    public AccessDeniedException(String message) {
        super(message);
    } 
}
