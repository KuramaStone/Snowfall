package me.brook.selection;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class ResourceManager {

	private Map<String, BufferedImage> resources;

	public ResourceManager() {
		resources = new HashMap<>();

		loadResource("berry", "berries.png");
		loadResource("meat", "meat.png");
	}

	public BufferedImage getResource(String id) {
		return resources.get(id);
	}

	private void loadResource(String id, String string) {

		try {
			BufferedImage image = ImageIO.read(getClass().getResource("/res/" + string));

			resources.put(id, image);

		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

}
