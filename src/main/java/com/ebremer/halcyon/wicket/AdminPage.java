package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.lib.HalcyonSettings;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;

/**
 *
 * @author erich
 */
public class AdminPage extends BasePage {
    
    public AdminPage() {
        add(new WebMarkupContainer("embedme") {
            @Override
            public void onComponentTag(ComponentTag tag) {
                super.onComponentTag(tag);
                tag.put("src", "/auth/admin/"+HalcyonSettings.realm+"/console/?response_type#/Halcyon/users");
            }
        });
    }
}
