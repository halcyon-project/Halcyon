package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.gui.HalcyonApplication;
import org.apache.wicket.Application;

public class DatabaseLocator {
    public static DataCore getDatabase() {
	HalcyonApplication app = (HalcyonApplication)Application.get();
	return app.getDataCore();
    }
}