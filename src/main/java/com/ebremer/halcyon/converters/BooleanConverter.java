package com.ebremer.halcyon.converters;

import com.beust.jcommander.IStringConverter;

public class BooleanConverter implements IStringConverter<Boolean> {

    @Override
    public Boolean convert(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Boolean value cannot be null or empty");
        }

        String lowerCaseValue = value.toLowerCase();
        if (null == lowerCaseValue) {
            throw new IllegalArgumentException("Invalid boolean value: " + value);
        } else switch (lowerCaseValue) {
            case "true":
            case "t":
            case "1":
                return true;
            case "false":
            case "f":
            case "0":
                return false;
            default:
                throw new IllegalArgumentException("Invalid boolean value: " + value);
        }
    }
}
