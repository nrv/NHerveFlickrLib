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

package name.herve.flickrlib;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;

import name.herve.flickrlib.filters.FlickrSearchResponseFilter;
import plugins.nherve.toolbox.Algorithm;

/**
 * 
 * @author Nicolas HERVE - n.herve@laposte.net
 */
public class FlickrFrontend {
	private final static String API_URL = "https://api.flickr.com/services/rest/";

	private String applicationKey;
	private boolean debug;
	private String endpoint;
	private Map<Integer, FlickrLicense> licenses;
	private Random rand;

	public FlickrFrontend(String key) {
		super();
		setDebug(false);
		rand = new Random(System.currentTimeMillis());

		applicationKey = key;
		endpoint = API_URL + "?api_key=" + applicationKey;
		licenses = null;
	}

	public void checkConnection() throws FlickrException {
		send("flickr.test.echo", null);
	}

	private List<FlickrImage> getFromXml(String fullXml, FlickrProgressListener l) throws FlickrException {
		List<FlickrImage> result = new ArrayList<FlickrImage>();

		result.addAll(getFromXmlAsList(fullXml, l));

		return result;
	}

	private FlickrSearchResponseData getFromXmlAsData(String fullXml) throws FlickrException {
		FlickrSearchResponseData data = FlickrXmlParser.asResponseData(fullXml);

		for (FlickrImage img : data.getPictures()) {
			populateLicense(img);
			populateAvailableSizes(img, null);
		}

		return data;
	}

	private List<FlickrImage> getFromXmlAsList(String fullXml, FlickrProgressListener l) throws FlickrException {
		l.notifyNewProgressionStep("Parsing images response");
		List<String> imagesXml = FlickrXmlParser.splitImagesXml(fullXml);
		if (imagesXml.size() == 0) {
			throw new FlickrException("No image found");
		}

		List<FlickrImage> result = new ArrayList<FlickrImage>();

		for (String xml : imagesXml) {
			FlickrImage img = FlickrXmlParser.parseImage(xml);
			populateLicense(img);
			result.add(img);
		}

		return result;
	}

	public FlickrLicense getLicense(int id) throws FlickrException {
		if (licenses == null) {
			String fullXml = send("flickr.photos.licenses.getInfo", null);

			licenses = new HashMap<Integer, FlickrLicense>();
			for (String xml : FlickrXmlParser.splitLicensesXml(fullXml)) {
				FlickrLicense l = FlickrXmlParser.parseLicense(xml);
				licenses.put(l.getId(), l);
			}
		}

		return licenses.get(id);
	}

	private List<FlickrImage> getRandomFromXml(String fullXml, int max, FlickrProgressListener l) throws FlickrException {
		l.notifyNewProgressionStep("Parsing images response");
		List<String> imagesXml = FlickrXmlParser.splitImagesXml(fullXml);
		if (imagesXml.size() == 0) {
			throw new FlickrException("No image found");
		}

		List<FlickrImage> result = new ArrayList<FlickrImage>();

		do {
			int choosen = rand.nextInt(imagesXml.size());
			String xmlChoosen = imagesXml.get(choosen);
			FlickrImage img = FlickrXmlParser.parseImage(xmlChoosen);
			populateLicense(img);
			imagesXml.remove(choosen);
			result.add(img);
		} while (!imagesXml.isEmpty() && (result.size() < max));

		return result;
	}

	public FlickrImage getRandomInterestingImage(FlickrProgressListener l) throws FlickrException {
		return getRandomInterestingImage(1, l).get(0);
	}

	public List<FlickrImage> getRandomInterestingImage(int max, FlickrProgressListener l) throws FlickrException {
		String fullXml = send("flickr.interestingness.getList&extras=license", l);
		return getRandomFromXml(fullXml, max, l);
	}

	public FlickrImage getRandomRecentImage(FlickrProgressListener l) throws FlickrException {
		return getRandomRecentImage(1, l).get(0);
	}

	public List<FlickrImage> getRandomRecentImage(int max, FlickrProgressListener l) throws FlickrException {
		String fullXml = send("flickr.photos.getRecent&extras=license", l);
		return getRandomFromXml(fullXml, max, l);
	}

	public FlickrImage getRandomSearchByTagImage(String tags, FlickrProgressListener l) throws FlickrException {
		return getRandomSearchByTagImage(tags, 1, l).get(0);
	}

	public List<FlickrImage> getRandomSearchByTagImage(String tags, int max, FlickrProgressListener l) throws FlickrException {
		return getRandomFromXml(searchByTags(tags, l), max, l);
	}

	public List<FlickrImage> getSearchByExpertQuery(String query, FlickrProgressListener l) throws FlickrException {
		return getFromXml(searchByExpertQuery(query, l), l);
	}

	public boolean isDebug() {
		return debug;
	}

	public BufferedImage loadImage(FlickrImage fi, String size, FlickrProgressListener l) throws FlickrException {
		URL url = fi.getImageURL(size);
		log("Loading " + fi.getId() + " - " + url);
		return loadImage(url, l);
	}

	private BufferedImage loadImage(URL url, FlickrProgressListener l) throws FlickrException {
		try {
			if (l != null) {
				l.notifyNewProgressionStep("Downloading image");
			}

			URLConnection uc = url.openConnection();
			uc.setDefaultUseCaches(false);
			uc.setUseCaches(false);
			uc.setRequestProperty("Cache-Control", "no-cache");
			uc.setRequestProperty("Pragma", "no-cache");

			InputStream in = uc.getInputStream();
			int len = uc.getContentLength();

			final int READ_BLOCKSIZE = 64 * 1024;
			final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			final byte[] data = new byte[READ_BLOCKSIZE];

			try {
				int off = 0;
				int count = 0;

				while (count >= 0) {
					count = in.read(data);
					if (count < 0) {
						if ((len != -1) && (off != len))
							throw new EOFException("Unexpected end of data at " + off + " (" + len + " expected)");
					} else {
						off += count;
					}

					if (count > 0) {
						buffer.write(data, 0, count);
					}

					if (l != null) {
						if (!l.notifyProgress(off, len)) {
							in.close();
							System.out.println("Interrupted by user.");
							return null;
						}
					}
				}
			} finally {
				in.close();
			}

			ByteArrayInputStream is = new ByteArrayInputStream(buffer.toByteArray());

			BufferedImage img = ImageIO.read(is);
			is.close();

			return img;
		} catch (Throwable e) {
			throw new FlickrException(e);
		}
	}

	public BufferedImage loadImageBiggestAvailableSize(FlickrImage fi, FlickrProgressListener l) throws FlickrException {
		populateAvailableSizes(fi, l);
		return loadImage(fi, fi.getBiggestAvailableSize(), l);
	}

	public BufferedImage loadImageThumbnail(FlickrImage fi, FlickrProgressListener l) throws FlickrException {
		populateAvailableSizes(fi, l);
		return loadImage(fi, "Thumbnail", l);
	}

	private void log(String message) {
		if (isDebug()) {
			Algorithm.out("[Flickr] " + message);
		}
	}

	void populateAvailableSizes(FlickrImage img, FlickrProgressListener l) throws FlickrException {
		if (!img.isSizesDone()) {
			if (l != null) {
				l.notifyNewProgressionStep("Getting available sizes");
			}
			String fullXml = send("flickr.photos.getSizes&photo_id=" + img.getId(), l);
			List<String> sizesXml = FlickrXmlParser.splitSizesXml(fullXml);
			for (String sz : sizesXml) {
				img.addAvailableSize(FlickrXmlParser.parseSize(sz));
			}
			img.setSizesDone(true);
		}
	}

	void populateLicense(FlickrImage img) throws FlickrException {
		if ((img.getLicense() == null) && (img.getLicenseId() != null)) {
			img.setLicense(getLicense(Integer.parseInt(img.getLicenseId())));
		}
	}

	public FlickrSearchResponse search(FlickrSearchQuery query) throws FlickrException {
		return new FlickrSearchResponse(this, query);
	}

	public FlickrSearchResponse search(FlickrSearchQuery query, FlickrSearchResponseFilter filter) throws FlickrException {
		return new FlickrSearchResponse(this, query, filter);
	}

	FlickrSearchResponseData searchAsData(FlickrSearchQuery query) throws FlickrException {
		return getFromXmlAsData(searchByExpertQuery(query.getEffectiveQuery(), null));
	}

	private String searchByExpertQuery(String query, FlickrProgressListener l) throws FlickrException {
		if ((query == null) || (query.length() == 0)) {
			throw new FlickrException("Invalid query");
		}

		return send("flickr.photos.search&extras=license&" + query, l);
	}

	private String searchByTags(String tags, FlickrProgressListener l) throws FlickrException {
		if (tags == null) {
			throw new FlickrException("Invalid tags");
		}

		StringTokenizer stk = new StringTokenizer(tags, " ");

		if (stk.countTokens() == 0) {
			throw new FlickrException("Invalid tags");
		}

		String newTags = "";
		while (stk.hasMoreTokens()) {
			if (newTags.length() > 0) {
				newTags += ",";
			}
			newTags += stk.nextToken();
		}

		return send("flickr.photos.search&extras=license&tag_mode=all&sort=interestingness-desc&tags=" + newTags, l);
	}

	private String send(String method, FlickrProgressListener l) throws FlickrException {
		if (l != null) {
			l.notifyNewProgressionStep("Sending a query");
		}
		try {
			URL url = new URL(endpoint + "&method=" + method);
			log("Sending " + url.toString());
			URLConnection uc = url.openConnection();
			uc.setDefaultUseCaches(false);
			uc.setUseCaches(false);
			uc.setRequestProperty("Cache-Control", "no-cache");
			uc.setRequestProperty("Pragma", "no-cache");

			final BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));

			if (l != null) {
				l.notifyNewProgressionStep("Getting a response");
			}

			String response = "";
			String temp;
			while ((temp = in.readLine()) != null)
				response += temp + "\n";

			in.close();

			log("Receiving " + response);

			if (!response.contains("<rsp stat=\"ok\">")) {
				throw new FlickrException("Call failed : " + response);
			}

			in.close();

			return response;

		} catch (MalformedURLException e) {
			throw new FlickrException(e);
		} catch (IOException e) {
			throw new FlickrException(e);
		}
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
}
