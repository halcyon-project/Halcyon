/*
 * Software by Erich Bremer
 * ALL RIGHTS RESERVED
 */

package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.imagebox.Enums.ImageFormat;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Erich Bremer
 */
public class IIIFProcessor {
    private static final Pattern PATTERN1 = Pattern.compile("(.*)?/(\\d*),(\\d*),(\\d*),(\\d*)/(\\d*),/(\\d*)/default.(jpg|png|json)");
    private static final Pattern PATTERN2 = Pattern.compile("(.*)?/full/(\\d*),/(\\d*)/default.(jpg|png|json)");
    private static final Pattern INFO = Pattern.compile("(.*)?/info.json");
    
    private Matcher matcher;
    public URI uri = null;
    public int x;
    public int y;
    public int w;
    public int h;
    public int tx;
    public int ty;
    public int rotation;
    public boolean tilerequest = false;
    public boolean inforequest = false;
    public boolean fullrequest = false;
    public ImageFormat imageformat;

    IIIFProcessor(String url) throws URISyntaxException {
        matcher = PATTERN1.matcher(url);
        if (matcher.find()) {
            tilerequest = true;
            uri = new URI(matcher.group(1).replace(" ", "%20"));
            x = Integer.parseInt(matcher.group(2));
            y = Integer.parseInt(matcher.group(3));
            w = Integer.parseInt(matcher.group(4));
            h = Integer.parseInt(matcher.group(5));
            tx = Integer.parseInt(matcher.group(6));
            rotation = Integer.parseInt(matcher.group(7));
            if (null != matcher.group(8)) switch (matcher.group(8)) {
                case "jpg":
                    imageformat = ImageFormat.JPG;
                    break;
                case "png":
                    imageformat = ImageFormat.PNG;
                    break;
                case "json":
                    imageformat = ImageFormat.JSON;
                    break;
                default:
                    break;
            }
        } else {
            matcher = INFO.matcher(url);
            if (matcher.find()) {
                String xw = matcher.group(1);
                //System.out.println("info request --> "+w);
                inforequest = true;
                uri = new URI(xw.replace(" ", "%20"));
            } else {
                matcher = PATTERN2.matcher(url);
                if (matcher.find()) {
                    System.out.println("it's a default req");
                    tilerequest = true;
                    uri = new URI(matcher.group(1));
                    x = 0;
                    y = 0;
                    w = Integer.MAX_VALUE;
                    h = Integer.MAX_VALUE;
                    tx = Integer.parseInt(matcher.group(2));
                    rotation = Integer.parseInt(matcher.group(3));
                    fullrequest = true;
                    if (null != matcher.group(4)) switch (matcher.group(4)) {
                        case "jpg":
                            imageformat = ImageFormat.JPG;
                            break;
                        case "png":
                            imageformat = ImageFormat.PNG;
                            break;
                        case "json":
                            imageformat = ImageFormat.JSON;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }
}
