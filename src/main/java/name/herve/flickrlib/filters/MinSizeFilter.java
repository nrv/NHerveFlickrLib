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

import name.herve.flickrlib.FlickrImageSize;
import name.herve.flickrlib.FlickrImage;

/**
 * 
 * @author Nicolas HERVE - n.herve@laposte.net
 */
public class MinSizeFilter implements FlickrSearchResponseFilter {
	public MinSizeFilter(int min) {
		super();
		this.min = min;
	}

	private int min;

	@Override
	public boolean match(FlickrImage img) {
		if (img.isSizesDone()) {
			for (FlickrImageSize sz : img.getSizes()) {
				if ((sz.getWidth() >= min) && (sz.getHeight() >= min)) {
					return true;
				}
			}
		}
		return false;
	}

}
