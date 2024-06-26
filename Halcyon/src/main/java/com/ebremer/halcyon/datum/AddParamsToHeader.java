package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.gui.HalcyonSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 *
 * @author erich
 */
public class AddParamsToHeader extends HttpServletRequestWrapper {
    public static final String HID = "HALCYONPRINCIPAL";
    
    public AddParamsToHeader(HttpServletRequest request) {
        super(request);
    }

    public AddParamsToHeader(HttpServletRequest request, HashMap list) {
        super(request);
        //this.list = list;
    }
    
    @Override
    public String getHeader(String name) {
        if (HID.equals(name)) {
            return HalcyonSession.get().getHalcyonPrincipal().getUserURI();
        }
        /*
        if (list.containsKey(name)) {
            return list.get(name);
        }*/
        String header = super.getHeader(name);
        return (header != null) ? header : super.getParameter(name);
    }

    @Override
    public Enumeration getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        names.add(HID);
        /*
        list.keySet().forEach(i->{
            names.add(i);
        });
*/
        names.addAll(Collections.list(super.getParameterNames()));
        return Collections.enumeration(names);
    }
    
    @Override
    public Enumeration getHeaders(String name) {
        if (HID.equals(name)) {
            List<String> values = new ArrayList<>(1);
            values.add(HalcyonSession.get().getHalcyonPrincipal().getUserURI());
            return Collections.enumeration(values);
        }
        return super.getHeaders(name);
    }
}
