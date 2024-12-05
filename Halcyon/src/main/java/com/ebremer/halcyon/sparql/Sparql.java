package com.ebremer.halcyon.sparql;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

/**
 *
 * @author erich
 */
//https://triply.cc/docs/yasgui-api
public class Sparql extends BasePage {

    public Sparql() {
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(Sparql.class, "yasgui.min.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(Sparql.class, "yasgui.min.js")));
        HalcyonSession hs = HalcyonSession.get();
        HalcyonPrincipal hp = hs.getHalcyonPrincipal();
        String tokenScript = String.format(
                """                
                var token = '%s';
                var useriri = '%s';
                var userName = '%s';
                function isTokenExpired(token) {
                    const base64Url = token.split('.')[1];
                    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
                    const decodedToken = JSON.parse(window.atob(base64));
                    const expirationTime = decodedToken.exp * 1000;
                    return Date.now() > expirationTime;
                }
                function checkToken() {
                    if (isTokenExpired(token)) {
                        alert('Your session has expired. Please log in again.');
                        window.location.href = '/invalidateSession';
                    }
                }
                setInterval(checkToken, 60000);
                """,
                hp.getToken(),
                hp.getUserURI(),
                hp.getPreferredUserName()                
        );
        response.render(JavaScriptHeaderItem.forScript(tokenScript, "token-check"));
    }
}
