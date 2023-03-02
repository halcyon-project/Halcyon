package com.ebremer.halcyon.gui;

import java.net.MalformedURLException;
import java.net.URL;
 
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.core.util.resource.UrlResourceStream;
import org.apache.wicket.core.util.resource.locator.ResourceStreamLocator;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.string.Strings;
 
public class MyOwnStreamLocator extends ResourceStreamLocator {
    @Override
    public IResourceStream locate(Class<?> clazz, String path) {	
        System.out.println("Locating : "+path);
        String location;	
        String extension = path.substring(path.lastIndexOf('.') + 1);
	String simpleFileName = Strings.lastPathComponent(clazz.getName(), '.');
	location = "/gui/vendor/openseadragon/images/" + simpleFileName + "." + extension;
        System.out.println("RESOURCE : "+location);
	URL url;
	try {
            url = WebApplication.get().getServletContext().getResource(location);
            if (url != null) {
		return new UrlResourceStream(url);
            }
	} catch (MalformedURLException e) {
            throw new WicketRuntimeException(e);
	}
 	return super.locate(clazz, path);
    }
}