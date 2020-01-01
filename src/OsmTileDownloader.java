package org.aprsdroid.app;

import org.mapsforge.android.maps.mapgenerator.tiledownloader.TileDownloader;
import org.mapsforge.core.Tile;

public class OsmTileDownloader extends TileDownloader {
	private static final String HOST_NAME = "tiles.openstreetmap.org";
	private static final byte ZOOM_MAX = 18;
	private final StringBuilder stringBuilder = new StringBuilder();

	public OsmTileDownloader() {
	}

	public String getHostName() {
		return HOST_NAME;
	}

	public String getProtocol() {
		return "https";
	}

	public String getTilePath(Tile tile) {
		this.stringBuilder.setLength(0);
		this.stringBuilder.append('/');
		this.stringBuilder.append(tile.zoomLevel);
		this.stringBuilder.append('/');
		this.stringBuilder.append(tile.tileX);
		this.stringBuilder.append('/');
		this.stringBuilder.append(tile.tileY);
		this.stringBuilder.append(".png");
		return this.stringBuilder.toString();
	}

	public byte getZoomLevelMax() {
		return ZOOM_MAX;
	}
}
