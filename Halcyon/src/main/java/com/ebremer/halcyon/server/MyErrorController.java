package com.ebremer.halcyon.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders every Spring-dispatched error as a small self-contained HTML page carrying the real
 * status and the {@code sendError}/exception message.
 *
 * <p>This used to return the view names {@code error401}/{@code error403}/{@code error500},
 * which exist nowhere in the application — so every error, including deliberate user-facing
 * {@code sendError} messages such as the WebID login's, dissolved into a masked
 * {@code 404 "No static resource error500"}. Rendering directly, with no view resolution,
 * cannot miss.
 */
@Controller
@RequestMapping({"${server.error.path:${error.path:/error}}"})
public class MyErrorController extends BasicErrorController {

    @Autowired
    public MyErrorController(ErrorAttributes errorAttributes) {
        super(errorAttributes, new ErrorProperties());
    }

    @RequestMapping(
        produces = {"text/html"}
    )
    @Override
    public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response) {
        final HttpStatus status = getStatus(request);
        Map<String, Object> attrs = getErrorAttributes(request,
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE, ErrorAttributeOptions.Include.PATH));
        // Both are request-influenced (a sendError message often echoes request parameters) — escape.
        String message = HtmlUtils.htmlEscape(String.valueOf(attrs.getOrDefault("message", "")));
        String path = HtmlUtils.htmlEscape(String.valueOf(attrs.getOrDefault("path", "")));
        response.setStatus(status.value());
        View view = (model, req, resp) -> {
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write("""
                    <!doctype html><html><head><meta charset="utf-8"><title>%d %s</title></head>
                    <body style="font-family:sans-serif;max-width:40em;margin:3em auto">
                    <h2>%d %s</h2>
                    <p>%s</p>
                    <p style="color:#666">%s</p>
                    </body></html>
                    """.formatted(status.value(), status.getReasonPhrase(),
                            status.value(), status.getReasonPhrase(), message, path));
        };
        return new ModelAndView(view);
    }
}
