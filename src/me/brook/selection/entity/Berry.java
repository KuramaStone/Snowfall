package me.brook.selection.entity;

import java.awt.Color;

import me.brook.selection.World;
import me.brook.selection.tools.Vector2;

public class Berry extends Entity {

	private long max;

	private boolean isInFoodRegion = true;
	private double skillFactor; // record skill factor when created for sizing purposes

	public Berry(World world, double energy, Vector2 location) {
		super(world, energy, location, new Color(255, 246, 0), 1);
		setIconID("assets/berry.png");
		max = Long.MAX_VALUE;

		skillFactor = world.getSkillFactor();
		setDigestionDifficulty(0.01);
	}

	@Override
	public void tick(World world) {
		super.tick(world);
		if(age % 1000 == 0) {
			isInFoodRegion = world.isInFoodRegion(this.getLocation());
		}

		addEnergy(-1 * (isInFoodRegion ? .1 : .4));
		size = (getEnergy() / 30 / skillFactor);

		if(getEnergy() <= 0) {
			die(world, "eaten");
		}
	}

}
