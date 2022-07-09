package me.brook.selection.tools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Noise {

	private Random rnd;
	private int seed;
	private int extraSeed;

	private int layers;
	private double persistance, freq;
	private double scale;

	public Noise(int layers, double scale, double persistance, double period,
			int seed, int extraSeed) {
		this.layers = layers;
		this.scale = scale;
		this.freq = period;
		this.persistance = persistance;
		this.extraSeed = extraSeed;

		if(seed == -1) {
			rnd = new Random();
			seed = rnd.nextInt();
			rnd = new Random(seed);
		}
		else {
			rnd = new Random(seed);
		}

		this.seed = seed;
		SimplexNoise.setSeed(seed);
	}

	/*
	 * Basic brownian fractal noise
	 */
	public double getSmoothNoiseAt(double x, double y, double z) {
		x *= scale;
		y *= scale;

		double total = 0;
		double frequency = 1;
		double amplitude = 1;
		double maxValue = 0; // Used for normalizing result to 0.0 - 1.0

		for(int i = 0; i < this.layers; i++) {
			total += SimplexNoise.noise(x * frequency, y * frequency, z * frequency + seed) * amplitude;
			maxValue += amplitude;

			frequency *= freq;
			amplitude *= this.persistance;
		}

		return total / maxValue;
	}

	/*
	 * Basic brownian fractal noise
	 */
	public double getFadedNoiseAt(double x, double y, double z) {
		x *= scale;
		y *= scale;

		double total = 0;
		double frequency = 1;
		double amplitude = 1;
		double maxValue = 0; // Used for normalizing result to 0.0 - 1.0

		for(int i = 0; i < this.layers; i++) {
			total += Math.cos(SimplexNoise.noise(x * frequency, y * frequency, z * frequency + seed)) * amplitude;
			maxValue += amplitude;

			frequency *= freq;
			amplitude *= this.persistance;
		}

		return total / maxValue;
	}

	private double fade(double t) {
		// return 6 * (t * t * t * t * t) - 15 * t + 10 * (t * t * t);
		return 1 - 30 * ((Math.pow(t, 5)) / 5 - (Math.pow(t, 4)) / 2 + (Math.pow(t, 3)) / 3);
	}

	public long getSeed() {
		return seed;
	}

	public void setSeed(int seed) {
		this.seed = seed;
		SimplexNoise.setSeed(seed);
	}

}
