package com.ebremer.halcyon.wicket.osd;

import org.apache.wicket.model.IModel;

/**
 * Typed model that returns the current progress for the ProgressBar component.
 * 
 * @author Christopher Hlubek (hlubek)
 * 
 */
public abstract class OSDModel implements IModel<OSD>
{

	private static final long serialVersionUID = 1L;

	@Override
	public final OSD getObject()
	{
		return getosd();
	}

	/**
	 * Return the progress in form of a Progression value object.
	 * 
	 * @return the progress
	 */
	protected abstract OSD getosd();

}
