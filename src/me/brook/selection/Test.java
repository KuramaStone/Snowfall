package me.brook.selection;

import static java.lang.Math.*;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.badlogic.gdx.math.Vector3;

import me.brook.selection.tools.Noise;
import me.brook.selection.tools.Vector2;

public class Test {

	public static void main(String[] args) {
		Random random = new Random(1234);

		Noise seafloorMap = new Noise(6, (1.0 / 1800) * 3, 0.8, 3, 1234, 4214);
		Noise cliffMap = new Noise(3, (1.0 / 1800) * 6, 0.25, 2, 1234, 4214);
		Noise caveMap = new Noise(3, (1.0 / 1800) * 10, 0.25, 2, 1234, 4214);
		Noise wormMap = new Noise(3, (1.0 / 1800) * 50, 0.5, 2, 1234, 4214);
		Noise distortionMap = new Noise(3, (1.0 / 1800) * 25, 0.5, 2, 1234, 4214);

		Vector2 p1 = new Vector2(0.52, 0.02);
		Vector2 p2 = new Vector2(1, 1);

		JFrame frame = new JFrame();
		frame.setTitle("Test Seabed");
		frame.setSize(1800, (int) Math.floor(1800 / (16.0 / 9.0)));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);

		frame.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {

				double speed = 0.02;
				if(e.getKeyCode() == KeyEvent.VK_W) {
					p1.y += speed;
					p1.y = max(0, min(1, p1.y));
				}
				else if(e.getKeyCode() == KeyEvent.VK_A) {
					p1.x -= speed;
					p1.x = max(0, min(1, p1.x));
				}
				else if(e.getKeyCode() == KeyEvent.VK_S) {
					p1.y -= speed;
					p1.y = max(0, min(1, p1.y));
				}
				else if(e.getKeyCode() == KeyEvent.VK_D) {
					p1.x += speed;
					p1.x = max(0, min(1, p1.x));
				}

				if(e.getKeyCode() == KeyEvent.VK_UP) {
					p2.y += speed;
					p2.y = max(0, min(1, p2.y));
				}
				else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
					p2.x -= speed;
					p2.x = max(0, min(1, p2.x));
				}
				else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
					p2.y -= speed;
					p2.y = max(0, min(1, p2.y));
				}
				else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
					p2.x += speed;
					p2.x = max(0, min(1, p2.x));
				}

			}
		});
		
		System.out.println(Math.signum(-1) * pow(-1, 1));

		JPanel panel = new JPanel();
		frame.setContentPane(panel);

		frame.setVisible(true);

		BufferedImage buffer = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = (Graphics2D) buffer.getGraphics();
		Graphics g = panel.getGraphics();

		while(true) {
			g2.setColor(new Color(0f, 0.1f, .5f));
			g2.fillRect(0, 0, panel.getWidth(), panel.getHeight());

			for(int x = 0; x < panel.getWidth(); x++) {
				for(int y = 0; y < panel.getHeight(); y++) {
					double x1 = x + (10 * distortionMap.getSmoothNoiseAt(x, y, 1));
					
					Vector3 color = new Vector3(252, 225, 180); // sand;
					int alpha = 255;
					double seabedTurb = seafloorMap.getSmoothNoiseAt(x1, 1, 1);
					double cliffTurb = cliffMap.getSmoothNoiseAt(x1, 1, 1);
					double caveNoise = caveMap.getSmoothNoiseAt(x1, y, 1);

					seabedTurb = Math.signum(seabedTurb) * pow(seabedTurb, 1);

					int floorStart = 50;

					floorStart = (int) Math.round(floorStart + seabedTurb * 25);
					floorStart = (int) Math.round(floorStart + getCurveValueAt(p1, p2, abs(cliffTurb)) * frame.getHeight() * 2);

					if(y > floorStart) {
						alpha = 0;
					}
					
					
					if(alpha == 255) {
						if(caveNoise < -0.5)
							alpha = 0;
					}
					

					int rgb = new Color((int) color.x, (int) color.y, (int) color.z, alpha).getRGB();
					if(alpha != 0)
						buffer.setRGB(x, y, rgb);
				}

			}

			// draw curve
			for(int x = 0; x < 100; x++) {
				double dx = (((double) x) / 100);
				int y = (int) (getCurveValueAt(p1, p2, dx) * 100);

				int r = 2;
				g2.setColor(Color.BLACK);
				g2.fillOval(x - r, y - r, r * 2, r * 2);

			}

			int worms = 3;
			int index = 0;
			random.setSeed(3162412);
			int seed = random.nextInt(10000);
			for(int x = 0; x <= worms; x++) {
				for(int y = 0; y <= worms; y++) {
					random.setSeed(index * 12526);

					Vector2 start = new Vector2(((double) x / worms) * panel.getWidth(), ((double) y / worms) * panel.getHeight());
					start = start.add(random.nextDouble() * (1.0 / (worms + 1)) * panel.getWidth() * 0.1,
							random.nextDouble() * (1.0 / (worms + 1)) * panel.getHeight() * 0.1);
					Vector2 pos = start.copy();

					int length = 2000;
					for(int j = 0; j < length; j++) {
						double theta = wormMap.getSmoothNoiseAt(index * 1254251, j, seed);
						double size = 10 + 9 * (signum(theta) * (pow(abs(theta), 0.5)));

						g2.setColor(new Color(0f, 0.1f, .5f));
						g2.fillOval((int) (pos.x - size / 2), (int) (pos.y - size / 2), (int) size, (int) size);
						pos = pos.add(new Vector2((signum(theta) * (pow(abs(theta), 0.5))) * (Math.PI)).multiply(size / 2));
					}
					index++;
				}
			}

			int r = 10;
			g2.setColor(Color.PINK);
			g2.fillOval((int) (p1.x * panel.getHeight() - r), (int) (p1.y * panel.getHeight() - r), r * 2, r * 2);
			g2.fillOval((int) (p2.x * panel.getHeight() - r), (int) (p2.y * panel.getHeight() - r), r * 2, r * 2);

			g.drawImage(buffer, 0, panel.getHeight(), panel.getWidth(), -panel.getHeight(), null);
		}

	}

	public static double getCurveValueAt(Vector2 p1, Vector2 p2, double x) {
		double y = 0;

		if(p1.x > p2.x) {
			Vector2 temp = p1;
			p1 = p2;
			p2 = temp;
		}

		if(x < p1.x) {
			y = cosInterpolate(0, p1.y, x / p1.x); // interpolate between 0 and p1.y
		}
		else if(x > p1.x && x < p2.x) {
			y = cosInterpolate(p1.y, p2.y, (x - p1.x) / (p2.x - p1.x)); // interpolate between p1.y and p2.y
		}
		else { // interpolate between p2.y and 1
			y = cosInterpolate(p2.y, 1, (x - p2.x) / (1 - p2.x));
		}

		return y;
	}

	public static double cosInterpolate(double y1, double y2, double fraction) {
		double mu2 = (1 - cos(fraction * 3.141)) / 2;

		return (y1 * (1 - mu2) + y2 * mu2);
	}

	public static Vector3 interpolate(Vector3 a, Vector3 b, float fraction) {

		float DELTA_RED = b.x - a.x;
		float DELTA_GREEN = b.y - a.y;
		float DELTA_BLUE = b.z - a.z;

		float red = a.x + (DELTA_RED * fraction);
		float green = a.y + (DELTA_GREEN * fraction);
		float blue = a.z + (DELTA_BLUE * fraction);

		return new Vector3(max(0, min(255, red)), max(0, min(255, green)), max(0, min(255, blue)));
	}

	public static double interpolate(double a, double b, double t) {
		double interp = a + ((b - a) * t);
		return interp;
	}

}
