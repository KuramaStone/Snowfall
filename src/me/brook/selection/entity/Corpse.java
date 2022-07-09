package me.brook.selection.entity;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

import me.brook.selection.World;
import me.brook.selection.tools.Vector2;

public class Corpse extends Entity {

	public Corpse(World world, double energy, Vector2 location) {
		super(world, energy, location, new Color(196, 92, 90), 1);
		setIconID("assets/berry.png");

		size = Math.sqrt(energy / Math.PI) * 0.33; // inverse of entity formula to use same size

		setDigestionDifficulty(0.05);
		sensorHormones = buildHormones(1, 0, 0, (float) energy, 0, 0, 0);
	}

	@Override
	public boolean isCorpse() {
		return true;
	}
	
	@Override
	public void addEnergy(double energy) {
		super.addEnergy(energy);
		sensorHormones = buildHormones(1, 0, 0, (float) energy, 0, 0, 0);
	}

	@Override
	public void tick(World world) {
		super.tick(world);

		size = Math.sqrt(energy / Math.PI) * 0.33; // inverse of entity formula to use same size
		if(size < 5)
			size = 5;

		this.applyForce(new Vector2(-Math.PI / 2).multiply(getMass()));

		if(age > 50000) {
			die(world, "existed too much");
		}

		Rectangle2D bounds = world.getBorders().getBounds();
		
		addEnergy(-1 * 0.02 * Math.pow(1.00001, age * 50));

		if(getEnergy() <= 0) {
			die(world, "eaten");
		}
	}

	@Override
	public void buildBody() {
		super.buildBody();
		// body.setGravityScale(0.5);
		// body.setLabel("");
	}

}
