/*
 * Copyright 2011-2013 Nicolas Hervé.
 * 
 * This file is part of FlickrLib.
 * 
 * FlickrLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FlickrLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with FlickrLib. If not, see <http://www.gnu.org/licenses/>.
 */

package name.herve.flickrlib.filters;

import java.util.ArrayList;

import name.herve.flickrlib.FlickrImage;

/**
 * 
 * @author Nicolas HERVE - n.herve@laposte.net
 */
public class ChainedFilters extends ArrayList<FlickrSearchResponseFilter> implements FlickrSearchResponseFilter {
	private static final long serialVersionUID = 4993153025996779181L;

	@Override
	public boolean match(FlickrImage img) {
		for (FlickrSearchResponseFilter f : this) {
			if (!f.match(img)) {
				return false;
			}
		}
		return true;
	}

}
