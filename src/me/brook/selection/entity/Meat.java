package me.brook.selection.entity;

import java.awt.Color;

import me.brook.selection.World;
import me.brook.selection.tools.Vector2;

public class Meat extends Entity {

	private double rotTime;
	private boolean isInFoodRegion = true;
	private double skillFactor; // record skill factor when created for sizing purposes

	public Meat(World world, double energy, Vector2 location) {
		super(world, energy, location, new Color(196, 92, 90), 1);
		setIconID("assets/meat.png");

		rotTime = Long.MAX_VALUE;

		skillFactor = world.getSkillFactor();
		setDigestionDifficulty(0.05);
	}

	@Override
	public void tick(World world) {
		super.tick(world);
		if(age % 1000 == 0) {
			isInFoodRegion = world.isInFoodRegion(this.getLocation());
		}
		
		addEnergy(-5 * (isInFoodRegion ? .1 : .4));
		size = (getEnergy() / 50 / skillFactor);

		if(getEnergy() <= 0) {
			die(world, "eaten");
		}

	}

}
