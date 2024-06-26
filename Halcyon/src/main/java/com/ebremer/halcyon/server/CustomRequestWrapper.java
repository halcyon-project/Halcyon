package com.ebremer.halcyon.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;

public class CustomRequestWrapper extends HttpServletRequestWrapper {
    private final Map<String, String[]> additionalParams;

    public CustomRequestWrapper(HttpServletRequest request) {
        super(request);
        additionalParams = new HashMap<>(request.getParameterMap());
    }

    public void addParameter(String name, String value) {
        additionalParams.put(name, new String[]{value});
    }

    @Override
    public String getParameter(String name) {
        String[] values = additionalParams.get(name);
        if (values != null && values.length > 0) {
            return values[0];
        }
        return super.getParameter(name);
    }
}
