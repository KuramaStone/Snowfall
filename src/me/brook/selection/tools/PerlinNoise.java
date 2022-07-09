package me.brook.selection.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class PerlinNoise {

	private float freq = 1;

	private Random rnd;
	private long seed;
	private long extraSeed;

	private int layers;
	private float scale;

	private float PERSISTANCE = 0.5f;
	private float PERIOD_EXPO = 2.5f;

	public static void main(String[] args) {
		Random random = new Random();

		BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
		Noise gen = new Noise(3, 0.003, 0.8f, 2, random.nextInt(100000), 0);
		
		try {
			ImageIO.write(image, "png", new File("C:\\Users\\Brookie\\Documents\\Programming\\noise.png"));
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel panel = new JPanel();
		frame.setContentPane(panel);
		frame.setSize(512, 512);
		frame.setVisible(true);

		int length = 100000;
		List<Point2D> particles = new ArrayList<>(length);
		for(int i = 0; i < length; i++) {
			particles.add(new Point2D.Double(random.nextInt(image.getWidth()), random.nextInt(image.getHeight())));
		}

		Graphics2D g2 = (Graphics2D) image.getGraphics();
		
		Color water = new Color(0f, 0.1f, .5f);

		double i = 0;
		while(true) {
			i += 1 / 400.0;

			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, image.getWidth(), image.getHeight());

//			for(int x = 0; x < image.getWidth(); x++) {
//				for(int y = 0; y < image.getHeight(); y++) {
//					double x0 = x - 250;
//					double y0 = y - 250;
//
//					double noise = ((gen.getSmoothNoiseAt(x0, y0, i) + 1) / 2);
//
//					image.setRGB(x, y, interpolateColor(water, Color.WHITE, (float) noise).getRGB());
//				}
//			}
			for(Point2D p : particles) {
				double theta = ((gen.getSmoothNoiseAt(p.getX() - 250, p.getY() - 250, i) + 1) / 2) * (Math.PI * 2) + Math.PI / 2;
				Vector2 move = new Vector2(theta).multiply(1);

				p.setLocation(p.getX() + move.x, p.getY() + move.y + 1);

				if(p.getX() < 0 || p.getX() >= image.getWidth() || p.getY() < 0 || p.getY() >= image.getHeight()) {
					p.setLocation(random.nextInt(image.getWidth()), random.nextInt(image.getHeight()));
				}

				image.setRGB((int) p.getX(), (int) p.getY(), 0);
			}

			panel.getGraphics().drawImage(image, 0, 0, 500, 500, null);
		}

	}

	private static Color interpolateColor(Color COLOR1, Color COLOR2, float fraction) {
		final float INT_TO_FLOAT_CONST = 1f / 255f;
		fraction = Math.min(fraction, 1f);
		fraction = Math.max(fraction, 0f);

		final float RED1 = COLOR1.getRed() * INT_TO_FLOAT_CONST;
		final float GREEN1 = COLOR1.getGreen() * INT_TO_FLOAT_CONST;
		final float BLUE1 = COLOR1.getBlue() * INT_TO_FLOAT_CONST;
		final float ALPHA1 = COLOR1.getAlpha() * INT_TO_FLOAT_CONST;

		final float RED2 = COLOR2.getRed() * INT_TO_FLOAT_CONST;
		final float GREEN2 = COLOR2.getGreen() * INT_TO_FLOAT_CONST;
		final float BLUE2 = COLOR2.getBlue() * INT_TO_FLOAT_CONST;
		final float ALPHA2 = COLOR2.getAlpha() * INT_TO_FLOAT_CONST;

		final float DELTA_RED = RED2 - RED1;
		final float DELTA_GREEN = GREEN2 - GREEN1;
		final float DELTA_BLUE = BLUE2 - BLUE1;
		final float DELTA_ALPHA = ALPHA2 - ALPHA1;

		float red = RED1 + (DELTA_RED * fraction);
		float green = GREEN1 + (DELTA_GREEN * fraction);
		float blue = BLUE1 + (DELTA_BLUE * fraction);
		float alpha = ALPHA1 + (DELTA_ALPHA * fraction);

		red = Math.min(red, 1f);
		red = Math.max(red, 0f);
		green = Math.min(green, 1f);
		green = Math.max(green, 0f);
		blue = Math.min(blue, 1f);
		blue = Math.max(blue, 0f);
		alpha = Math.min(alpha, 1f);
		alpha = Math.max(alpha, 0f);

		return new Color(red, green, blue, alpha);
	}

	public PerlinNoise(int layers, float scale, float persistance, float period,
			long seed, long extraSeed) {
		this.layers = layers;
		this.scale = scale;
		this.PERSISTANCE = persistance;
		this.PERIOD_EXPO = period;
		this.extraSeed = extraSeed;

		if(seed == -1) {
			rnd = new Random();
			seed = rnd.nextLong();
			rnd = new Random(seed);
		}
		else {
			rnd = new Random(seed);
		}

		this.seed = seed;
	}

	public float getPerlinAt(float x, float y) {
		x *= scale;
		y *= scale;
		float[] smooth = new float[layers];

		for(int l = 0; l < layers; l++) {
			rnd.setSeed((long) (l * 5124908 - l + seed));
			extraSeed = rnd.nextLong();
			smooth[l] = smoothen(l, x, y);
		}

		float perlin = 0;

		float persistance = PERSISTANCE;
		float amp = 1.0f;
		float totalAmp = 0;

		for(int l = layers - 1; l >= 0; l--) {
			amp *= persistance;
			totalAmp += amp;

			perlin += smooth[l] * amp;
		}

		perlin /= totalAmp;

		return perlin;
	}

	private float smoothen(int layer, float x, float y) {
		float smooth = 0;

		int period = (int) Math.pow(PERIOD_EXPO, layer);
		float frequency = freq / period;

		int x0 = (int) (Math.floor(x / period)) * period;
		int x1 = (x0 + period);
		float horizontal = (x - x0) * frequency;

		int y0 = (int) (Math.floor(y / period)) * period;
		int y1 = (y0 + period);

		float vertical = (y - y0) * frequency;

		float top = smoothStep(noiseAt(x0, y0), noiseAt(x1, y0),
				horizontal);

		float bottom = smoothStep(noiseAt(x0, y1), noiseAt(x1, y1),
				horizontal);

		smooth = smoothStep(top, bottom, vertical);

		return smooth;
	}

	private float lerp(float i, float j, float a) {
		return i + a * (j - i);
	}

	private float smoothStep(float i, float j, float w) {
		float value = w * w * w * (w * (w * 6f - 15f) + 10f);

		return i + value * (j - i);
	}

	private float noiseAt(float x, float y) {
		rnd.setSeed(seed + (long) (y * 694269) + (long) (x * 420420) + extraSeed);
		float value = rnd.nextFloat() * 2 - 1;

		return value;
	}

}
