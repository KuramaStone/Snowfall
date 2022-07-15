package me.brook.selection.entity;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

import me.brook.selection.World;
import me.brook.selection.tools.Vector2;

public class Corpse extends Entity {

	private double mass;
	private double startMass;
	private double startEnergy;

	public Corpse(World world, double mass, double energy, Vector2 location) {
		super(world, energy, location, new Color(196, 92, 90), 1);
		setIconID("assets/berry.png");
		this.mass = mass;
		this.startEnergy = energy;
		this.startMass = mass;

		size = Math.sqrt(energy / Math.PI) * 0.33; // inverse of entity formula to use same size

		setDigestionDifficulty(0.05);
		sensorHormones = buildHormones(1, 0, 0, (float) energy, 0, 0, 0);
	}
	
	@Override
	public SaveState createSaveState() {
		SaveState state = super.createSaveState();
		state.map.put("corpseMass", this.mass);
		
		return state;
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
	public double getMass() {
		return mass;
	}
	
	@Override
	public void tick(World world) {
		super.tick(world);

		size = Math.sqrt(Math.max(0, energy) / Math.PI) * 0.33; // inverse of entity formula to use same size
		if(size < 5)
			size = 5;

		this.applyForce(new Vector2(-Math.PI / 2).multiply(getMass() * 2));

		if(age > 50000) {
			die(world, "existed too much");
		}

		addEnergy(-1 * 0.1 * Math.pow(1.00001, age * 50 * (1 + world.getLivingPopulation() / 500.0)));
		this.mass = (this.startMass * Math.max(0.15, energy / this.startEnergy));

		if(getEnergy() <= 0) {
			die(world, "eaten");
		}
		
		if(world.getBorders().intersects(this.location))
			die(world, "fought the law");
	}

	@Override
	public void buildBody() {
		super.buildBody();
		// body.setGravityScale(0.5);
		// body.setLabel("");
	}

}
