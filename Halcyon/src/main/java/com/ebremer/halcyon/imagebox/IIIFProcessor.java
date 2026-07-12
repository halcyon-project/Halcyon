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
import org.slf4j.LoggerFactory;

/**
 *
 * @author Erich Bremer
 */
public class IIIFProcessor {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IIIFProcessor.class);
    // One grammar for tile requests: the region is either the IIIF "full"
    // keyword or x,y,w,h — combined with any supported size form ("w,h",
    // "w," or best-fit "!w,h"). This lets clients fetch whole-image
    // renditions (e.g. the Zephyr minimap's /full/!360,270/0/default.jpg)
    // through the same code path as ordinary tiles; ImageServer swaps the
    // image's real dimensions in for "full" once metadata is loaded.
    private static final Pattern TILE = Pattern.compile("(.*)?/(?:(full)|(\\d+),(\\d+),(\\d+),(\\d+))/([!0-9,]*)/(\\d+)/default\\.(jpg|png|json|ttl)");
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
    public boolean scalex = false;
    public boolean scaley = false;
    public boolean aspectratio = false;
    public ImageFormat imageformat;

    IIIFProcessor(String url) throws URISyntaxException {
        matcher = TILE.matcher(url);
        if (matcher.find() && !matcher.group(7).isEmpty()) {
            tilerequest = true;
            uri = new URI(matcher.group(1).replace(" ", "%20"));
            if (matcher.group(2) != null) {
                // "full": the entire image. Placeholder extents here — the
                // servlet clamps them to the image's true width/height.
                fullrequest = true;
                x = 0;
                y = 0;
                w = Integer.MAX_VALUE;
                h = Integer.MAX_VALUE;
            } else {
                x = Integer.parseInt(matcher.group(3));
                y = Integer.parseInt(matcher.group(4));
                w = Integer.parseInt(matcher.group(5));
                h = Integer.parseInt(matcher.group(6));
            }
            String[] sizes = matcher.group(7).split(",");
            if (sizes[0].startsWith("!")) {
                aspectratio = true;
                tx = Integer.parseInt(sizes[0].substring(1));
                ty = (sizes.length > 1 && !sizes[1].isEmpty()) ? Integer.parseInt(sizes[1]) : tx;
            } else {
                scalex = true;
                tx = Integer.parseInt(sizes[0]);
                ty = (sizes.length > 1 && !sizes[1].isEmpty()) ? Integer.parseInt(sizes[1]) : 0;
            }
            rotation = Integer.parseInt(matcher.group(8));
            imageformat = switch (matcher.group(9)) {
                case "jpg" -> ImageFormat.JPG;
                case "png" -> ImageFormat.PNG;
                case "json" -> ImageFormat.JSON;
                case "ttl" -> ImageFormat.TTL;
                default -> null;
            };
        } else {
            matcher = INFO.matcher(url);
            if (matcher.find()) {
                String xw = matcher.group(1);
                inforequest = true;
                uri = new URI(xw.replace(" ", "%20"));
            }
        }
    }
}
