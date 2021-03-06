package me.brook.selection.entity;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import me.brook.neat.GeneticCarrier;
import me.brook.neat.network.NeatNetwork;
import me.brook.neat.randomizer.SexualReproductionMixer;
import me.brook.neat.randomizer.SexualReproductionMixer.Crossover;
import me.brook.neat.species.InnovationHistory;
import me.brook.neat.species.Species;
import me.brook.selection.World;
import me.brook.selection.entity.body.Hitbox;
import me.brook.selection.entity.body.Hitbox.CellCollisionInfo;
import me.brook.selection.entity.body.Hitbox.CellInfo;
import me.brook.selection.entity.body.Segment;
import me.brook.selection.entity.body.Structure;
import me.brook.selection.entity.body.Structure.Face;
import me.brook.selection.tools.Border;
import me.brook.selection.tools.Icecore;
import me.brook.selection.tools.Vector2;

public abstract class Agent extends Entity implements GeneticCarrier, Serializable {

	private static final long serialVersionUID = -1033773389567070076L;

	protected static final int entitiesToTrack = 1;
	// sunlight, hue, relative x, relative y, energy, speed, nearby count, to light
	protected static final int totalInputs = 12 + 7, outputs = 11;

	public static final int ENERGY_LOC = 9;
	public static final int LOCX_LOC = 0;
	public static final int LOCY_LOC = 1;
	public static final int AGE_LOC = 8;
	public static final int LIVING_LOC = 12;
	public static final int ROTATION_LOC = 10;
	public static final int MATURITY_LOC = 22;
	public static final int CMETABOLISM_LOC = 14;

	protected transient Random random;

	public float sight_range; // allows precise knowledge over a smaller range
	protected double strength;

	protected boolean isFlipped = false;

	// entity utilities
	protected int eyes = 1;
	protected float FOV = 180;
	protected boolean isBerry;

	// rendering info
	public int generation = 1;
	public String label = "";
	private Border shape;

	// Neural Network
	protected boolean hasBrain = true;
	protected transient NeatNetwork brain;
	protected transient Species<Agent> parentSpecies;

	// fitness tracking
	public double fitness = 69420;
	public double fitnessSum;
	protected double fitnessPenalty = 1;
	public long deathAt;

	protected float energyConsumed = 1;
	protected int biteDelay;

	protected float movementTotal = 1;
	protected int berriesEaten;
	private int sleepDelay = 0;

	protected List<EntityBite> stomach;
	protected double stomachCapacity;
	protected double digestionSpeed;

	protected boolean wantsToReproduce = false;
	protected boolean wantsToSleep = false;

	// phenotype
	protected int max_age;
	protected double sleepNeeded; // amount of sleep needed
	protected double sleepFrequency; // time until sleep is mandatory. measured in percentage of max life.
	private double energyPercentageForChild;

	// entity options
	protected boolean moveAtMaxiumum = false;
	protected boolean canReproduce = false;
	protected boolean canAge;

	protected boolean seesWalls = true;
	protected boolean hasCollisionWithAgents = false;
	protected boolean canBeCarnivorous = false;
	protected double energyModifier = 1;

	protected double sleepingModifier = 0.1;

	protected transient List<NearbyEntry> lastNearbyEntities;
	private double lastDistanceToClosestFood = 0;

	private double wantsToDigest;

	private Agent parent;

	private long reproductionDelay = 0;
	protected long movementPaused = 0;
	protected int children = 0;

	private boolean wantsToEat;

	protected double[] lastOutputs;

	protected double currentMetabolism, baseMetabolism;

	protected double[] noveltyMetrics;

	protected double maturity;

	protected double density;

	protected boolean wantsToPhotosynthesize;
	protected boolean wantsToChemosynthesize;
	protected double wantsToEjectFood;
	protected double wantsToHeal;
	protected double wantsToGrow;
	protected boolean wantsToAttack;
	protected boolean wantsToHold;

	protected double geneMultiplier, geneFactor;

	protected double entitiesBlockingLightFactor, entitiesBlockingChemsFactor;

	protected double carnGained;
	protected double sunGained;
	protected double chemGained;
	protected double energyUsed;

	private double bodyMaintainanceEnergy;
	protected Entity closestEntity;

	protected float hue;

	private double speedGene;
	private double dietGene;

	protected double health, maxHealth;
	protected int ticksSinceLastHurt = 1000000;

	protected Sprite bodySprite;
	protected Structure structure;
	protected String bodyDNA = "A";// "A.2B.3C.1E";
	private boolean isActive = false;

	private int cudaID;

	protected float lastLightExposure, lastChemicalExposure;

	protected Hitbox hitbox;

	protected float lastHealth = 0;

	protected List<Entity> hasCollidedWith;
	protected Map<Entity, List<CellCollisionInfo>> collisionInfo;
	public String textureStatus;

	protected boolean isAttacking;

	protected double[] strucDevelopment;
	protected double developmentLevel;
	protected double energyIncome, energySpending;

	public Agent(World world, Species<Agent> parentSpecies, Color color, double strength, double size,
			Vector2 location) {
		this(world, parentSpecies, color, strength, size, location, false);
	}

	public Agent(World world, Species<Agent> parentSpecies, Color color, double strength, double size,
			Vector2 location,
			boolean disableBrain) {
		super(world, 1, location, color, size);
		setIconID("assets/agent.png");
		this.parentSpecies = parentSpecies;
		this.strength = strength;
		this.age = 1;
		this.size = size;
		this.color = color;
		stomach = new ArrayList<>();
		random = new Random();
		lastNearbyEntities = new ArrayList<>();

		velocity = new Vector2();
		acceleration = new Vector2();
		this.relative_direction = random.nextDouble() * Math.PI * 2;
		lastNearbyEntities = new ArrayList<>();

		setDigestionDifficulty(0.08);
		hasCollidedWith = new ArrayList<>();
		collisionInfo = new HashMap<>();
	}

	public Agent() {
	}

	protected double[] hormoneState;

	public void createHormoneState() {

		int total = 0;
		// these are the output hormones that feed into themselves
		int movementHormones = total += 3;
		int rotationHormones = total += 1;
		int passiveEnergyGainHormones = total += 2;
		int activeEnergyGainHormones = total += 2;
		int digestHormones = total += 1;
		int holdHormones = total += 1;
		int growHormones = total += 1;
		int healHormones = total += 1;
		int blankHormones = total += 3; // blank hormones for it to play with

		hormoneState = new double[total];

	}

	public Hitbox buildHitbox(Vector2 location, float theta) {
		Rectangle2D bounds = structure.getBounds();
		Vector2 coreOffset = structure.getCoreOffset();

		float x = location.x;
		float y = location.y;
		float sizePerSeg = getSizeOfSegment();
		float w = (float) bounds.getWidth() * sizePerSeg;
		float h = (float) bounds.getHeight() * sizePerSeg;

		float zoom = 1f;
		float ox = (float) ((coreOffset.x + 0.5) * sizePerSeg);
		float oy = (float) ((coreOffset.y + 0.5) * sizePerSeg);
		ox = (ox - w * 0.5f) * zoom + w * 0.5f;
		oy = (oy - h * 0.5f) * zoom + h * 0.5f;

		x -= ox;
		y -= oy;

		Vector2 low = new Vector2(x, y);
		Vector2 high = new Vector2(x + w, y + h);

		Hitbox hitbox = new Hitbox(structure, low, high);
		hitbox.setOrigin(new Vector2(x + ox, y + oy));
		hitbox.setCurrentTheta(theta);
		hitbox.build();

		return hitbox;
	}

	public Hitbox buildHitbox() {
		return buildHitbox(this.location, (float) this.relative_direction);
	}

	public Hitbox getHitbox() {
		return hitbox;
	}

	public Color createRandomColor() {
		Random random = new Random(System.nanoTime());

		float h = random.nextFloat();

		// ignore yellow hues since they blend into the background
		while(h * 360 > 30 && h * 360 < 60)
			h = random.nextFloat();

		float s = random.nextFloat() * 0.5f + 0.5f;
		float b = random.nextFloat() * 0.5f + 0.5f;

		return Color.getHSBColor(h, s, b);
	}

	public abstract NeatNetwork createBrain();

	/*
	 * The number of ticks to apply the last outputs
	 */

	public void tick(World world) {
		super.tick(world);
		if(!isAlive()) {
			return;
		}
		if(structure == null) {
			return;
		}

		if(this.health <= 0) {
			die(world, "no health points");
			return;
		}

		this.attachedEntities.removeIf(e -> (e == null || (!e.isAlive() || !e.shouldExist())));

		// calculate maturity
		this.maturity = calculateMaturity();
		this.currentMetabolism = (0.5 + this.baseMetabolism) * (1 - Math.max(0, this.maturity - 1));
		if(currentMetabolism < 0) {
			currentMetabolism = 0;
		}
		if(maturity > 2.0 && canAge) {
			die(world, "old age");
			return;
		}

		// eat food
		// if(this.biteDelay-- < 0 && wantsToEat) {
		// lookForFood();
		// }

		if(this.intersectsWall(this.location, (float) relative_direction)) {
			stuckInWall();
		}
		digest();

		useEnergy();
		reproduction();

		if(!wantsToHold) {
			detachAll();
		}
		if(Double.isNaN(getEnergy())) {
			die(world, "in this house we obey the laws of thermodynamics!");
		}

		ticksSinceLastHurt++;
	}

	protected double calculateMaturity() {
		return (this.age / ((structure.getStructure().size() * 1000.0)));
	}

	protected double calculateMaxAge() {
		return (2.0 * (structure.getStructure().size() * 1000));
	}

	public double getBaseMetabolism() {
		return baseMetabolism;
	}

	protected double chemosynthesis() {
		return 0;
	}

	protected double photosynthesis() {
		return 0;
	}

	@Override
	public double getDigestionDifficulty() {
		return 1;
	}

	private void digest() {
		if(wantsToDigest > 0) {
			if(!stomach.isEmpty()) {

				//
				for(EntityBite e : stomach) {
					// how much it resists digestion
					double difficulty = 1;
					double energyDissolved = e.getEnergy() * difficulty * wantsToDigest;
					energyConsumed += energyDissolved * getDietModifier(e.source.isBerry());

					double energyGained = energyDissolved * getDietModifier(false);
					addEnergy(energyGained);
					e.energy -= energyDissolved;

					carnGained += energyGained;
				}

				stomach.removeIf(e -> e.energy <= 1);
			}
		}
	}

	@Override
	public void die(World world, String reason) {
		if(!isAlive())
			return;
		super.die(world, reason);
		world.deathAt(this);

		// world.respawnAgent(false);
		// System.out.println(reason);
	}

	public void dispose() {
		super.dispose();
		lastNearbyEntities = null;
		hasCollidedWith = null;
		collisionInfo = null;
		parent = null;
		parentSpecies = null;
		stomach = null;
		brain = null;
		if(this.getBodySprite() != null)
			this.getBodySprite().getTexture().dispose();
	}

	private void reproduction() {
		if(canReproduce && wantsToReproduce && reproductionDelay < 0
				&& this.getEnergy() >= this.getReproductionEnergyThreshold()) {
			Agent ally = getBreedingAlly();

			reproduceWith(ally);
		}
		reproductionDelay--;
	}

	private void reproduceWith(Agent insertion) {

		Vector2 loc = this.location.copy();
		int index = 0;

		reproductionDelay = (int) (size); // wait x ticks before being able to breed again

		double energyTransfer = this.getReproductionEnergyThreshold();
		addEnergy(-energyTransfer);

		Agent child = (Agent) this.createClone();
		child.reset();

		if(insertion != null) {
			child.breed(insertion);
			insertion.children++;
		}
		world.addEntity(child);

		child.mutate(1);
		child.relative_direction = random.nextDouble() * 2 * Math.PI;

		child.generation = this.generation + 1;
		child.parent = this;

		double energyForChild = energyTransfer;

		double startHealth = (energyForChild / 2) / getEnergyPerHP(); // half goes into hp
		double maxHealth = child.getMaxHealth();
		child.health = startHealth;
		child.setEnergy(energyForChild / 2);
		// child.applyForce(random.nextDouble() * Math.PI * 2, child.getMass() * 0);

		child.buildBody();
		child.init();
		child.setLocation(loc); // temp location
		Hitbox hb = child.buildHitbox(loc, (float) child.getRelativeDirection());
		double distance = Math.max(child.structure.getBounds().getWidth(), child.structure.getBounds().getHeight()) * Agent.getSizeOfSegment();

		// arranged in order of likelihood
		while(loc == null || intersectsHitbox(hb) || intersectsNearbyAgent(hb) || doesLineIntersectWall(this.location.copy(), loc, 4)
				|| !world.getBorders().contains(loc.toPoint())) {
			loc = this.getLocation().copy()
					.add(new Vector2(random.nextDouble() * Math.PI * 2).multiply(distance * 4 * ((random.nextDouble() * 0.75) + 0.25)));
			hb = child.buildHitbox(loc, (float) child.getRelativeDirection());

			if(index++ > 100) {
				child = null;
				break;
			}
		}

		if(child == null) {
			return;
		}

		child.setLocation(loc);
		child.hitbox = child.buildHitbox();
		child.velocity = new Vector2();

		this.children++;
	}

	private boolean doesLineIntersectWall(Vector2 start, Vector2 end, int steps) {

		Vector2 increment = end.subtract(start).divide(steps);

		Vector2 loc = start.copy();
		for(int i = 0; i < steps; i++) {
			if(world.getBorders().intersects(loc))
				return true;

			loc = loc.add(increment);
		}

		if(world.getBorders().intersects(loc))
			return true;

		return false;
	}

	private boolean intersectsHitbox(Hitbox hb) {
		List<CellCollisionInfo> results = hb.intersectsHitbox(this.hitbox);
		if(results != null && !results.isEmpty()) {
			return true;
		}

		return false;
	}

	private boolean intersectsNearbyAgent(Hitbox hb) {

		for(Entry<Entity, EntityViewInfo> set : lastNearbyEntities) {
			Entity e = set.getKey();

			if(e.isAgent()) {
				List<CellCollisionInfo> results = hb.intersectsHitbox(((Agent) set.getKey()).getHitbox());
				if(results != null && !results.isEmpty()) {
					return true;
				}
			}
		}

		return false;
	}

	// gets an ally that is in range for breeding
	private Agent getBreedingAlly() {
		if(lastNearbyEntities == null)
			return null;

		try {
			List<Agent> compatible = new ArrayList<Agent>();

			// always prefer those with more energy
			for(Entry<Entity, EntityViewInfo> eyeData : lastNearbyEntities) {
				if(eyeData != null) {
					if(!eyeData.getKey().shouldExist())
						continue;
					if(eyeData.getKey().isAgent()) {
						Agent ent = (Agent) eyeData.getKey();

						if(ent.getSpecies().equals(this.getSpecies()))
							compatible.add(ent);
					}
				}

			}

			if(!compatible.isEmpty()) {
				return compatible.get(random.nextInt(compatible.size()));
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public boolean isHunter() {
		return this.structure.hasType(Structure.HUNT);
	}

	// if the Predator is mostly covering an Predator that is at least 50% of its
	// size,
	// then it eats it
	public void lookForFood() {
		if(!isHunter()) { // cant hunt without hunting cells
			return;
		}

		// List<SearchTask> tasks = new ArrayList<>();
		//
		// int threads = Math.max(4, world.getEntities().size() / 25);
		// for(int i = 0; i < threads; i++) {
		// tasks.add(new SearchTask(world, this, i, threads));
		// }

		// if(closestEntity != null) {
		// Entity e = closestEntity;
		//
		// if(isFood(e)) {
		//
		// if(this.canEat(e) && this.isEntityCloseEnoughToEat(e)) {
		// if(e.isAgent())
		// getAge();
		// eat(world, e);
		// }
		// }
		// }

	}

	/**
	 * @return Returns whether or not the entity is both edible
	 */
	public boolean isFood(Entity ent) {
		if(ent == this) {
			return false;
		}
		if(!ent.isAlive()) {
			return false;
		}

		return true;
	}

	/**
	 * @return Returns whether or not the entity is both edible and close enough to eat
	 */
	public boolean canEat(Entity ent) {
		if(!isFood(ent)) {
			return false;
		}

		if(!ent.isEdible()) {
			return false;
		}

		if(ent.isAgent()) {
			if(doesEntityFitInMouth(ent)) {
				return true;
			}
		}
		else {
			return true;
		}

		return false;
	}

	public void eat(World world, Entity ent) {
		if(ent.isBerry()) {
			Berry b = (Berry) ent;
			if(b.getAge() < 10) { // berry must have just spawned, remove and dont count it
				ent.die(world, "eaten early");
				return;
			}
		}
		else if(ent.isNaturalMeat()) {
			Meat m = (Meat) ent;
			if(m.getAge() < 10) { // berry must have just spawned, remove and dont count it
				ent.die(world, "eaten early");
				return;
			}
		}

		// speed modifier. agent must be going slowly to get useful amounts
		double biteSize = getMass() * speedGene * 10000;

		double energyBit = Math.min(ent.getEnergy(), biteSize);

		if(canStomachFit(energyBit) && canEat(ent)) {
			ent.addEnergy(-energyBit);

			// if entity dies, eat entire corpse too
			if(ent.getEnergy() <= 0) {
				ent.willDropCorpse = false;
				ent.die(world, "eaten alive");
				ent.setEnergy(0);
				energyBit += ent.getTotalBodyEnergy();
			}

			energyConsumed += energyBit;
			carnGained += energyBit;

			if(ent.isAgent())
				world.totalPreyGained += energyBit;
			else if(ent.isCorpse())
				world.totalScavGained += energyBit;

			// if(ent.isAgent()) {
			// System.out.println("cannibawism");
			// }
			// stomach.add(new EntityBite(ent, energyBit));
			addEnergy(energyBit);
		}

		biteDelay = 1;
	}

	@Override
	public boolean isAgent() {
		return true;
	}

	private boolean canStomachFit(double energy) {
		return true;
		// double maxEnergy = getMass() * 100000;
		// double energyInStomach = getEnergyInStomach();
		// return energyInStomach + energy < maxEnergy;
	}

	public double getEnergyInStomach() {
		if(stomach == null)
			return 0;
		double energyInStomach = 0;
		for(EntityBite e : stomach)
			energyInStomach += e.energy;
		return energyInStomach;
	}

	protected double getDietModifier(boolean isBerry) {

		// you always receive at least 50% of the energy and can get an additional 50% if you perfectly match

		if(isBerry) {
			return (0.5 + (dietGene * 0.5) / 2);
		}
		else {
			return (0.5 + -(dietGene * 0.5) / 2);
		}

	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public double getDietGene() {
		return dietGene;
	}

	public static class NearbyEntry implements Entry<Entity, EntityViewInfo> {

		protected Entity entity;
		protected EntityViewInfo info;

		public NearbyEntry(Entity entity, EntityViewInfo info) {
			super();
			this.entity = entity;
			this.info = info;
		}

		@Override
		public Entity getKey() {
			return entity;
		}

		@Override
		public EntityViewInfo getValue() {
			return info;
		}

		@Override
		public EntityViewInfo setValue(EntityViewInfo value) {
			EntityViewInfo old = this.info;
			this.info = value;
			return old;
		}

	}

	public static class EntityViewInfo {
		double distance;
		double angle;
		Entity entity;

		public EntityViewInfo(double distance, double angle, Entity entity) {
			this.distance = distance;
			this.angle = angle;
			this.entity = entity;
		}
	}

	public static Comparator<EntityViewInfo> viewDistanceSorter = new Comparator<EntityViewInfo>() {

		@Override
		public int compare(EntityViewInfo o1, EntityViewInfo o2) {
			return (int) Math.signum(o1.distance - o2.distance);
		}
	};

	public static Comparator<NearbyEntry> viewDistanceSorter2 = new Comparator<NearbyEntry>() {

		@Override
		public int compare(NearbyEntry o1, NearbyEntry o2) {
			return (int) Math.signum(o1.getValue().distance - o2.getValue().distance);
		}
	};

	public static class DistanceToOriginSorter implements Comparator<Entry<Entity, EntityViewInfo>> {

		@Override
		public int compare(Entry<Entity, EntityViewInfo> o1, Entry<Entity, EntityViewInfo> o2) {
			return (int) Math.signum(o1.getValue().distance - o2.getValue().distance);
		}

	}

	public boolean isEntityCloseEnoughToEat(Entity ent) {
		if(!collisionInfo.containsKey(ent))
			return false;
		List<CellCollisionInfo> info = collisionInfo.get(ent);

		// check if agent is near mouth
		if(!ent.isAgent()) {

			// if they collided with a hunter cell, eat em
			for(CellCollisionInfo cci : info) {
				if(cci.cell1.seg.getType().equals(Structure.HUNT))
					return true;
			}
		}
		else {

			// if they collided with a hunter cell AND the collision wasn't with a thrust cell, eat em
			for(CellCollisionInfo cci : info) {
				if(cci.cell1.seg.getType().equals(Structure.HUNT))
					if(!cci.cell2.seg.getType().equals(Structure.THRUST))
						return true;
			}

		}

		return false;
	}

	protected boolean doesEntityFitInMouth(Entity ent) {
		if(!ent.isAgent()) {
			return true;
		}
		else {
			Agent a = (Agent) ent;

			return (this.structure.getStructure().size()) > a.getStructure().getStructure().size();
		}
	}

	public double getEntitiesBlockingChemsFactor() {
		return entitiesBlockingChemsFactor;
	}

	private double lastForceOutput;

	private int segmentCount;

	public void calculateOutputs() {
		//
		// // intended amount of hormones. each agent can choose which hormones to respond to
		//
		// int total = 0;
		// // these are the output hormones that feed into themselves
		// int movementHormones = total += 3;
		// int rotationHormones = total += 1;
		// int passiveEnergyGainHormones = total += 2;
		// int activeEnergyGainHormones = total += 2;
		// int digestHormones = total += 1;
		// int holdHormones = total += 1;
		// int growHormones = total += 1;
		// int healHormones = total += 1;
		// int blankHormones = total += 3; // blank hormones for it to play with
		//
		// // these are input hormones. these hormone values are influenced directly and not kept in the hormone state
		// int senseHormones = total += this.sensorHormones.length; // the influence of nearby agents
		// int velocityDetection = total += 2; // vel x, vel y
		// int energyLevels = total += 1; // energy
		// int ageLevel = total += 1;
		// int chemAndPhotoLevels = total += 2;
		// int upSense = 1; // relative direction to light
		// int aggressionAwareness = total += 2; // is it attacking, and is it being attacked
		//
		// double[] inputs = new double[total];
		//
		// // first fill input with hormone states
		// int index = 0;
		// for(int i = 0; i < hormoneState.length; i++) {
		// inputs[index++] = hormoneState[i];
		// }
		//
		// // second, fill in the sensory hormones
		// for(int i = 0; i < sensorHormones.length; i++) {
		// inputs[index++] = sensorHormones[i];
		// }
		//
		// Vector2 myRelVelocity = this.velocity.rotate(new Vector2(), this.relative_direction).divide(1 + sight_range);
		// // fill in the rest of the hormones
		// inputs[index++] = myRelVelocity.x; // vel x hormone
		// inputs[index++] = myRelVelocity.y; // vel y hormone
		// inputs[index++] = 1.0 / (Math.max(1, this.energy));
		// inputs[index++] = structure.getSumDevelopmentOfGrownCells() / segmentCount;
		// inputs[index++] = lastLightExposure;
		// inputs[index++] = lastChemicalExposure;
		// inputs[index++] = relativeAngleTo(this.relative_direction, Math.PI);
		// inputs[index++] = 1.0 / (1 + getTicksSinceLastHurt());
		// inputs[index++] = isAttacking ? 1 : 0;
		//
		// brain.setInputs(inputs);
		// brain.calculate();
		// double[] outputs = brain.getOutput();
		// this.lastOutputs = outputs;
	}

	public void useBrainOutput() {
		double[] out = this.lastOutputs;
		if(out == null) {
			return;
		}

		if(!isAlive()) {
			return;
		}

		for(int i = 0; i < out.length; i++) {
			double o = out[i];

			if(Double.isNaN(o) || Double.isInfinite(o)) {
				out[i] = 0;
				throw new IllegalArgumentException("Output " + i + " is Infinite/NaN");
			}
		}

		// int total = 0;
		// // these are the output hormones that feed into themselves
		// int movementHormones = total += 3;
		// int rotationHormones = total += 1;
		// int passiveEnergyGainHormones = total += 2;
		// int activeEnergyGainHormones = total += 2;
		// int digestHormones = total += 1;
		// int holdHormones = total += 1;
		// int growHormones = total += 1;
		// int healHormones = total += 1;
		// int blankHormones = total += 3; // blank hormones for it to play with
		//
		// // alter current hormone state
		// for(int i = 0; i < outputs.length; i++) {
		// double v = outputs[i];
		// double sensitivity = 0.05;
		//
		// hormoneState[i] += v * sensitivity;
		// }
		//
		// // now read hormone state and take action
		//
		// double[] moveHormones = new double[movementHormones];
		// System.arraycopy(hormoneState, 0, moveHormones, 0, movementHormones);
		// Vector2 force = calculateForceWithHormones(moveHormones);
		// this.applyForce(force);

		double rotation = out[0];

		double desiredForceX = out[1];
		double desiredForceY = out[2];
		if(Math.abs(desiredForceX) < 0.01)
			desiredForceX = 0;
		if(Math.abs(desiredForceY) < 0.01)
			desiredForceY = 0;

		if(isSelected())
			if(Gdx.input.isKeyPressed(Input.Keys.J))
				if(Gdx.input.isKeyPressed(Input.Keys.H))
					desiredForceX = 1;
				else
					desiredForceX = -1;

		double changeAngleBy = (rotation) * Math.toRadians(4);

		this.nextRelativeDirection = (float) getTrueTheta(this.relative_direction + changeAngleBy);
		// this.relative_direction = -Math.PI / 2;

		wantsToReproduce = out[3] > 0.0;

		double speed = (this.speedGene * Math.pow(getMass(), 1)) * 1 * currentMetabolism;

		// rotate force relative to current direction
		Vector2 force = new Vector2(desiredForceX, desiredForceY).multiply(speed);
		force = force.rotate(new Vector2(), this.nextRelativeDirection);
		// if(isSelected())
		// System.out.printf("%s, %s, %s, %s\n", this.speedGene, Math.pow(getMass(), 1), currentMetabolism, force.toString());
		this.applyForce(force);
		lastForceOutput = Math.abs(desiredForceX) + Math.abs(desiredForceY);

		// only one energy access method can be used at a time
		wantsToPhotosynthesize = out[4] >= 0;
		wantsToChemosynthesize = out[5] >= 0;
		// wantsToEjectFood = out[6];
		wantsToHeal = out[6];
		wantsToAttack = out[7] >= 0;
		wantsToHold = out[8] > 0;
		wantsToDigest = Math.max(out[9], 0);
		wantsToGrow = Math.max(out[10], 0);

	}

	public double[] getNoveltyMetrics() {
		if(noveltyMetrics == null) {
			return calculateNoveltyMetrics();
		}

		return noveltyMetrics;
	}

	@Override
	public double getMass() {
		return getMass(true);
	}

	public double getMass(boolean useMaturity) {
		double maturityModifier = useMaturity ? Math.min(Math.max(maturity, 0.05), 1) : 1;
		double mass = (getSizeOfSegment()) * Math.pow(structure.getSumDevelopmentOfGrownCells(), 2) * density * maturityModifier;

		if(!Double.isFinite(mass))
			getAge();
		return mass;
	}

	////////////////////////////// energy methods

	protected void useEnergy() {
		if(!isAlive())
			return;
		if(age < 10)
			return;

		double sizeUsage = structure.getSumDevelopmentOfGrownCells() * 0.1 * currentMetabolism;
		double brainUsage = Math.pow(brain.getConnections().size() + brain.getTotalNeurons().size(), 2) * 0;
		double movementUsage = Math.abs(lastForceOutput) / Math.pow(getMass(), 2);
		double used = (sizeUsage + brainUsage + movementUsage) * energyModifier;
		// if(this.isSelected())
		// System.out.println(sizeUsage / energy);

		if(used < 0) {
			used = 0;
			System.err.printf("Used negative energy. Size: %s, Brain: %s, Movement: %s\n", sizeUsage, brainUsage, movementUsage);
		}

		this.energySpending += used;
		bodyMaintainanceEnergy += used;

		if(used > this.energy) {
			double remainder = (this.energy - used);
			// convert extra hp into energy
			double hpNeeded = remainder / getEnergyPerHP();
			this.health += hpNeeded;

			used = this.energy;
		}

		addEnergy(-used);
		energyUsed -= used;

		setEnergy(Math.max(0, this.getEnergy()));
	}

	protected double getEnergyPerUnit() {
		return .1;
	}

	protected void useRotationEnergy(double changeAngleBy) {
		// double inertia = 0.4 * (size * size) * (size / 2 * size / 2);
		// double used = 0.5 * inertia * (changeAngleBy * changeAngleBy) * energyModifier;
		// this.energy -= used;
	}

	@Override
	public void move() {
		applyBuoyancy();
		super.move();
	}

	private void applyBuoyancy() {
		// the water has a bouyancy too, so we subtract our bouyancy by its to get the actual result
		double buoyancyOfAgent = 1.5 * (Math.pow(10 / 2, 2) * Math.PI) * 1;
		double buoyancyOfWater = 1.5 * (25 * Math.PI) * 1;

		applyForce(Math.PI / 2, (buoyancyOfAgent - buoyancyOfWater));
	}

	protected boolean collideWithWall() {
		return true;
	}

	@Override
	public GeneticCarrier createClone() {
		Agent agent = null;
		return agent;
	}

	public float getSightRange() {
		return sight_range;
	}

	public double getStrength() {
		return strength;
	}

	public Color getColor() {
		return color;
	}

	public double getTotalBodyEnergy() {
		double bodyEnergy = bodyMaintainanceEnergy;
		double energy = this.energy;
		double hpEnergy = this.getHealth() * getEnergyPerHP();

		return (bodyEnergy + energy + hpEnergy);
	}

	public double getBodyMaintainanceEnergy() {
		return bodyMaintainanceEnergy;
	}

	public void setBodyMaintainanceEnergy(double bodyMaintainanceEnergy) {
		this.bodyMaintainanceEnergy = bodyMaintainanceEnergy;
	}

	public double getFitness() {
		return Math.max(Double.MIN_VALUE, fitness * fitnessPenalty);
	}

	public double getReproductionEnergyThreshold() {
		return maxHealth * getEnergyPerHP();
	}

	public void setFitness(double fitness) {
		this.fitness = fitness;
	}

	public boolean hasBrain() {
		return hasBrain;
	}

	public void disableBrain() {
		this.hasBrain = false;
		this.brain = null;
	}

	public boolean isBerry() {
		return isBerry;
	}

	public NeatNetwork getBrain() {
		return brain;
	}

	public void setBrain(NeatNetwork brain) {
		this.brain = brain;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void reset() {
		adjustForPhenotype();
		random = new Random(); // new random since cloning will cause it to be identical again

		this.segmentCount = this.bodyDNA.split("\\.").length;
		setAlive(true);

		bodyMaintainanceEnergy = 0;
		acceleration = new Vector2();
		velocity = new Vector2();
		reasonForDeath = "";

		noveltyMetrics = null;

		energyConsumed = 0;
		fitness = 0;
		fitnessPenalty = 1;
		// fitnessSum = 0; // keep the fitness sum through resets
		lastOutputs = null;

		age = 1;
		children = 0;

		movementTotal = 1;
		berriesEaten = 0;

		this.relative_direction = random.nextDouble() * Math.PI * 2;

		max_age = Integer.MAX_VALUE;
		maxHealth = this.getGeneDNA().split("\\.").length * 1000; // structure isn't built yet
		health = (getReproductionEnergyThreshold() / 2) / getEnergyPerHP(); // start with low hp
		setEnergy(getReproductionEnergyThreshold() / 2);
	}

	public double getHealth() {
		return health;
	}

	public void setHealth(double health) {
		this.health = health;
	}

	public double getRelativeDirection() {
		return relative_direction;
	}

	public double getTotalEnergyGained() {
		return sunGained + carnGained + chemGained;
	}

	public double getCarnGained() {
		return carnGained;
	}

	public double getChemGained() {
		return chemGained;
	}

	@Override
	public double calculateFitness() {

		fitness = 69;

		setFitness(fitness);
		return getFitness();
	}

	public void mutate(float multiplier) {
	}

	public void adjustForPhenotype() {

		this.hue = (float) this.getBrain().getPhenotype().getPhenotype("hue").getValue();
		while(hue > 1)
			hue -= 1;
		while(hue < 0)
			hue += 1;

		energyPercentageForChild = 1;// (double) this.getBrain().getPhenotype().getPhenotype("child_portion").getValue();
		this.baseMetabolism = this.getBrain().getPhenotype().getPhenotype("metabolism").getValue();

		this.density = this.getBrain().getPhenotype().getPhenotype("density").getValue();
		this.speedGene = this.getBrain().getPhenotype().getPhenotype("speed").getValue();
		this.dietGene = this.getBrain().getPhenotype().getPhenotype("pref_diet").getValue();
		this.geneMultiplier = this.getBrain().getPhenotype().getPhenotype("geneMultiplier").getValue();
		this.geneFactor = this.getBrain().getPhenotype().getPhenotype("geneFactor").getValue();

		setColor(new Color(Color.HSBtoRGB(hue, 1.0f, 1f)));

	}

	@Override
	public long getTimeOfDeath() {
		return deathAt;
	}

	@Override
	public void setTimeOfDeath(long time) {
		this.deathAt = time;
	}

	public int getBerriesEaten() {
		return berriesEaten;
	}

	@Override
	public GeneticCarrier breed(GeneticCarrier insertions) {
		SexualReproductionMixer.breed(this.network(), insertions.network(), this.getFitness() > insertions.getFitness(),
				Crossover.SINGLE, 0.1);
		this.reset();

		return this;
	}

	public float getEnergyConsumed() {
		return energyConsumed;
	}

	public int getVisionSpikes() {
		return eyes;
	}

	public float getFOV() {
		return FOV;
	}

	@Override
	public Species<? extends GeneticCarrier> getSpecies() {
		return parentSpecies;
	}

	@Override
	public void setSpecies(Species species) {
		this.parentSpecies = species;
	}

	public void setAsBerry(boolean isBerry) {
		this.isBerry = isBerry;
	}

	public Species<Agent> getParentSpecies() {
		return parentSpecies;
	}

	public Border getShape() {
		return shape;
	}

	public void setParent(Agent parent) {
		this.parent = parent;
	}

	public Agent getParent() {
		return parent;
	}

	public int getMaxAge() {
		return max_age;
	}

	public boolean wantsToReproduce() {
		return wantsToReproduce;
	}

	public float getMovementTotal() {
		return movementTotal;
	}

	public List<NearbyEntry> getLastNearbyEntities() {
		return lastNearbyEntities;
	}

	public InnovationHistory getGlobalInnovationHistory() {
		return world.getInnovationHistory();
	}

	public double getMaxSpeed() {
		return (size * size) / strength;
	}

	public int getChildren() {
		return children;
	}

	public static double relativeAngleTo(Vector2 from, Vector2 to) {
		// double angle = Math.atan2(to.y, to.x) - Math.atan2(from.y, from.x);
		double angle = Icecore.atan2(to.y, to.x) - Icecore.atan2(from.y, from.x);

		if(angle > Math.PI)
			angle -= 2 * Math.PI;
		else if(angle < -Math.PI)
			angle += 2 * Math.PI;

		return angle;
	}

	public static int getSizeOfSegment() {
		return 8;
	}

	public static double relativeAngleTo(double from, double to) {
		// double angle = Math.atan2(to.y, to.x) - Math.atan2(from.y, from.x);
		double angle = from - to;

		if(angle > Math.PI)
			angle -= 2 * Math.PI;
		else if(angle < -Math.PI)
			angle += 2 * Math.PI;

		return angle;
	}

	protected double getTrueTheta(double theta) {

		while(theta > Math.PI * 2) {
			theta -= Math.PI * 2;
		}
		while(theta < 0) {
			theta += Math.PI * 2;
		}

		return theta;
	}

	public double getEnergyUsed() {
		return energyUsed;
	}

	public double getSunGained() {
		return sunGained;
	}

	public double getEntitiesBlockingLightFactor() {
		return entitiesBlockingLightFactor;
	}

	public static class EntityBite {
		private double energy;
		private Entity source;

		public EntityBite(Entity source, double energy) {
			this.source = source;
			this.energy = energy;
		}

		public double getEnergy() {
			return energy;
		}

		public Entity getSource() {
			return source;
		}

	}

	public double getMaturity() {
		return maturity;
	}

	public double getCurrentMetabolism() {
		return currentMetabolism;
	}

	public double[] getLastOutputs() {
		return lastOutputs;
	}

	public double getDensity() {
		return density;
	}

	public int getGeneration() {
		return generation;
	}

	public float getHue() {
		return hue;
	}

	public void setHue(float hue) {
		this.hue = hue;
	}

	public String getGeneDNA() {
		return bodyDNA;
	}

	public void setRelativeDirection(double relative_direction) {
		this.relative_direction = relative_direction;
		this.nextRelativeDirection = (float) relative_direction;
		this.lastRotation = this.relative_direction;
	}

	protected float getSurfaceAreaForSegment(Segment seg, Vector2 sourceNormal) {
		// these are the normals for each face
		Vector2 side1 = new Vector2(0, 1).rotate(new Vector2(), this.relative_direction); // up
		Vector2 side2 = new Vector2(0, -1).rotate(new Vector2(), this.relative_direction); // down
		Vector2 side3 = new Vector2(1, 0).rotate(new Vector2(), this.relative_direction); // left
		Vector2 side4 = new Vector2(-1, 0).rotate(new Vector2(), this.relative_direction); // right

		float totalLightReceived = 0;
		if(!seg.hasNeighbor(Face.NORTH)) {
			totalLightReceived += Math.max(0, Math.cos(relativeAngleTo(side1, sourceNormal) + Math.PI));
		}
		if(!seg.hasNeighbor(Face.SOUTH)) {
			totalLightReceived += Math.max(0, Math.cos(relativeAngleTo(side2, sourceNormal) + Math.PI));
		}
		if(!seg.hasNeighbor(Face.EAST)) {
			totalLightReceived += Math.max(0, Math.cos(relativeAngleTo(side3, sourceNormal) + Math.PI));
		}
		if(!seg.hasNeighbor(Face.WEST)) {
			totalLightReceived += Math.max(0, Math.cos(relativeAngleTo(side4, sourceNormal) + Math.PI));
		}

		return totalLightReceived;
	}

	public String mutateBodyDNA(String dna, double nodeTypeChance, double dirAddChance, double dirDeleteChance,
			double copyChance, double deleteChance, double nodeAddChance, double angleChance, double swapChance) {

		StringBuilder sb = new StringBuilder("A.");

		String[] array = dna.split("\\.");

		List<Integer> swapped = new ArrayList<>();
		for(int a = 1; a < array.length; a++) {
			if(random.nextDouble() < swapChance) {
				int rndIndex = 1 + random.nextInt(array.length - 1);

				if(swapped.contains(a) || swapped.contains(rndIndex)) // don't swap twice
					continue;

				String str1 = array[a];
				String str2 = array[rndIndex];
				array[rndIndex] = str1;
				array[a] = str2;

				swapped.add(a);
				swapped.add(rndIndex);
			}
		}

		for(int a = 0; a < array.length; a++) {
			String chromo = array[a];

			if(a != 0 && random.nextDouble() < copyChance) {
				// swap with any value except one
				int rndIndex = 1 + random.nextInt(array.length - 1);
				chromo = array[rndIndex];
			}

			String directions = chromo.replaceAll("[\\D-]", "");
			String angle = chromo.replaceAll("[\\w^.-]", "");
			String type = chromo.replaceAll("[^a-zA-Z -]", "");

			if(random.nextDouble() < nodeAddChance) {
				int i = 1 + random.nextInt(4);
				String t = switch(i) {
					// case 0: {
					// // never a case 0 because cores cannot be mutated in.
					// }
					case 1 : {
						yield Structure.PHOTO;
					}
					case 2 : {
						yield Structure.CHEMO;
					}
					case 3 : {
						yield Structure.THRUST;
					}
					case 4 : {
						yield Structure.HUNT;
					}
					default:
						throw new IllegalArgumentException("Unexpected value: " + i);
				};

				sb = sb
						.append(directions) // start at this cell's loc
						.append(String.valueOf(random.nextInt(4)))
						.append(t)
						.append(".");
			}

			if(type.equals(Structure.CORE)) {
				// dont change core
				continue;
			}

			// double delete chance
			if(random.nextDouble() < deleteChance)
				continue;

			// mutate node location
			if(random.nextDouble() < dirAddChance) {
				directions += String.valueOf(random.nextInt(4)); // add
			}
			if(random.nextDouble() < dirDeleteChance) {
				if(!directions.isEmpty())
					directions = directions.substring(0, directions.length() - 1);
			}

			// if(random.nextDouble() < angleChance) {
			// int i = random.nextInt(4);
			// angle = "" + switch(i) {
			// case 0 : {
			// yield '!';
			// }
			// case 1 : {
			// yield '@';
			// }
			// case 2 : {
			// yield '#';
			// }
			// case 3 : {
			// yield '$';
			// }
			// default:
			// throw new IllegalArgumentException("Unexpected value: " + i);
			// };
			// }

			// mutate node type
			if(random.nextDouble() < nodeTypeChance) {
				int i = 1 + random.nextInt(4);
				type = switch(i) {
					// case 0: {
					// // never a case 0 because cores cannot be mutated in.
					// }
					case 1 : {
						yield Structure.PHOTO;
					}
					case 2 : {
						yield Structure.CHEMO;
					}
					case 3 : {
						yield Structure.THRUST;
					}
					case 4 : {
						yield Structure.HUNT;
					}
					default:
						throw new IllegalArgumentException("Unexpected value: " + i);
				};

			}

			// add chromosome to dna
			sb = sb.append(directions).append(type).append(angle).append(".");

		}
		return sb.toString();
	}

	public double getDevelopmentLevel() {
		return developmentLevel;
	}

	public void setDevelopmentLevel(double developmentLevel) {
		this.developmentLevel = developmentLevel;
	}

	@Override
	public SaveState createSaveState() {
		SaveState state = super.createSaveState();
		state.map.put("brain", this.brain);
		state.map.put("rotation", this.relative_direction);
		state.map.put("generations", this.generation);
		state.map.put("children", this.children);
		state.map.put("bodyEnergy", this.getBodyMaintainanceEnergy());
		state.map.put("bodyGene", this.bodyDNA);
		state.map.put("health", this.health);
		state.map.put("strucDevelopment", getStructureDevelopment());
		state.map.put("developmentLevel", this.developmentLevel);

		return state;
	}

	public boolean willDropCorpse() {
		return willDropCorpse;
	}

	public double getMaxHealth() {
		return maxHealth;
	}

	protected double getEnergyPerHP() {
		return 16;
	}

	public double damage(Entity attacker, double damage) {
		double finalDamage = damage / density;

		// dont deal more damage than health
		if(this.health < finalDamage)
			finalDamage = this.health;

		this.health -= finalDamage; // density acts a bit like armor
		if(this.health < 0)
			this.health = 0;

		ticksSinceLastHurt = 0;

		return finalDamage;
	}

	public Structure getStructure() {
		return structure;
	}

	@Override
	public void buildBody() {

		structure = new Structure(this.bodyDNA);
		structure.build(); // build structure from dna
		for(Segment seg : structure.getStructure())
			seg.setStrength(1);
		sight_range = (float) (4 * getSizeOfSegment() * Math.max(structure.getBounds().getWidth(), structure.getBounds().getHeight()));

		structure.setDevelopmentOfCell(0, 1); // core cell is grown by default

		if(strucDevelopment != null) {

			for(int i = 0; i < Math.min(strucDevelopment.length, structure.getStructure().size()); i++) {
				structure.setDevelopmentOfCell(i, strucDevelopment[i]);
			}

		}
	}

	public void setLoadedStructureDevelopment(double[] strucDevelopment) {
		this.strucDevelopment = strucDevelopment;
	}

	private double[] getStructureDevelopment() {
		double[] array = new double[structure.getStructure().size()];

		for(int i = 0; i < array.length; i++) {
			array[i] = structure.getDevelopmentOfCell(i);
		}

		return array;
	}

	public void setBodySprite(Sprite bodySprite) {
		this.bodySprite = bodySprite;
	}

	public Sprite getBodySprite() {
		return bodySprite;
	}

	public int getTicksSinceLastHurt() {
		return ticksSinceLastHurt;
	}

	public void setCudaID(int id) {
		this.cudaID = id;
	}

	public int getCudaID() {
		return cudaID;
	}

	public void setCurrentMetabolism(double currentMetabolism) {
		this.currentMetabolism = currentMetabolism;
	}

	public void setMaturity(double maturity) {
		this.maturity = maturity;
	}

	@Override
	protected Map<Entity, List<CellCollisionInfo>> broadphaseCollision(Vector2 temp, float rotation) {
		// check if location is valid e.g. it has no intersections

		collisionInfo.clear();
		// create hitbox at temp location
		Hitbox hitbox = this.buildHitbox(temp, rotation);

		if(lastNearbyEntities != null)
			for(NearbyEntry near : lastNearbyEntities) {

				if(near != null && near.entity.isAlive() && near.entity.shouldExist()) {

					if(near.entity.isAgent()) {

						Agent rude = (Agent) near.entity;
						List<CellCollisionInfo> collision = hitbox.intersectsHitbox(rude.hitbox);

						if(collision != null && !collision.isEmpty()) {
							collisionInfo.put(rude, collision);
						}
					}
					else {
						List<CellCollisionInfo> collision = hitbox.intersectsPoint(near.entity.getLocation(), (near.entity.size / 2 + Agent.getSizeOfSegment() / 2));
						if(collision != null && !collision.isEmpty()) {
							collisionInfo.put(near.entity, collision);
						}
					}

				}

			}

		return collisionInfo;
	}

	@Override
	protected boolean intersectsWall(Vector2 temp, float nextRelativeDirection2) {

		Hitbox hitbox = this.buildHitbox(temp, nextRelativeDirection2);
		for(CellInfo cell : hitbox.getCells()) {
			if(world.getBorders().intersects(cell.loc)) {
				return true;
			}
		}

		return false;
	}

	@Override
	protected boolean resolveCollisions(Map<Entity, List<CellCollisionInfo>> collidingWith) {
		if(collidingWith == null || collidingWith.isEmpty())
			return true;

		// check if location is valid e.g. it has no intersections

		boolean allowMovement = true;

		this.hasCollidedWith.clear();
		for(Entity entity : new HashSet<>(collidingWith.keySet())) {
			if(this.hasCollidedWith.contains(entity))
				continue;

			if(entity != null && entity.isAlive() && entity.shouldExist()) {

				if(entity.isAgent()) {
					Agent rude = (Agent) entity;

					// start with exchanging momentum
					double myMass = this.getTotalMass();
					double rudeMass = rude.getTotalMass();
					Vector2 myNewVel = this.velocity.multiply(myMass).add(rude.velocity.multiply(rudeMass)).divide(myMass + rudeMass);
					Vector2 rudeNewVel = rude.velocity.multiply(rudeMass).add(this.velocity.multiply(myMass)).divide(rudeMass + myMass);

					if(!myNewVel.isFinite() || !rudeNewVel.isFinite()) {
						getAge();
					}

					this.velocity = myNewVel;
					rude.velocity = rudeNewVel;

					// check if collision still exists after adding velocity

					// get two closest points and move them away from each other
					List<CellCollisionInfo> info = collidingWith.get(entity);

					Vector2 closestA = null, closestB = null;
					double dist = -1;

					for(CellCollisionInfo cci : info) {
						double d = cci.cell1.loc.distanceToRaw(cci.cell2.loc);

						if(dist == -1 || d < dist) {
							closestA = cci.cell1.loc;
							closestB = cci.cell2.loc;
							dist = d;
						}
					}

					boolean collides = false;
					if(dist != -1) {
						dist = Math.sqrt(dist);
						if(dist <= getSizeOfSegment() * 1.2) {
							collides = true;
						}
						if(dist <= getSizeOfSegment() * 0.9) { // only separate when they intersect too far
							collides = true;
							double lerp = (rudeMass / (myMass + rudeMass));
							double moveBy = getSizeOfSegment() - dist;
							Vector2 offset = new Vector2(closestA.atan2(closestB)).multiply(moveBy);
							if(!offset.isFinite())
								offset.isFinite();
							this.applyImpulse(offset.multiply(myMass * lerp));
							rude.applyImpulse(offset.multiply(-rudeMass * (1 - lerp)));

						}
					}

					this.hasCollidedWith.add(rude);

					if(collides)
						allowMovement = false;
				}
				else {
					if(wantsToHold)
						entity.attachTo(this);
				}

			}

		}

		return allowMovement;
	}

	@Override
	protected void stuckInWall() {
		this.health--; // lower h
	}

	@Override
	protected float getSurfaceArea(float theta) {
		float totalNormals = 0;

		for(Segment seg : structure.getStructure()) {
			float normals = getSurfaceAreaForSegment(seg, new Vector2(theta));

			if(Double.isNaN(normals) || Double.isInfinite(normals))
				getAge();

			totalNormals += normals;
		}

		return totalNormals * Agent.getSizeOfSegment();
	}

	public static double getSegmentSizeExtension() {
		return 1.5;
	}

	@Override
	public void setLocation(Vector2 location) {
		super.setLocation(location);
		this.lastLocation = location.copy();
	}

	public void setLastNearbyEntities(List<NearbyEntry> lastNearbyEntities) {
		this.lastNearbyEntities = lastNearbyEntities;
	}

	protected PolygonRegion computedShadowRegion;
	protected Vector2 lastLocation;
	protected double lastRotation;

	public void setComputedShadowRegion(PolygonRegion region) {
		this.computedShadowRegion = region;
	}

	public void createShadowRegion(int mapWidth, int mapHeight) {
		EarClippingTriangulator triangulator = new EarClippingTriangulator();

		Rectangle2D bounds = world.getBorders().getBounds2D();
		Vector2[] corners = hitbox.getRotatedCorners(hitbox.getOrigin(), hitbox.getCurrentTheta());

		Vector2 leftCorner = null;
		Vector2 rightCorner = null;
		Vector2 topCorner = null;
		Vector2 bottomCorner = null;
		for(Vector2 v2 : corners) {

			if(leftCorner == null || v2.x < leftCorner.x)
				leftCorner = v2;
			if(rightCorner == null || v2.x > rightCorner.x)
				rightCorner = v2;
			if(topCorner == null || v2.y > topCorner.y)
				topCorner = v2;
			if(bottomCorner == null || v2.y < bottomCorner.y)
				bottomCorner = v2;
		}
		leftCorner = toQuadVector(leftCorner, mapWidth, mapHeight, bounds);
		rightCorner = toQuadVector(rightCorner, mapWidth, mapHeight, bounds);
		topCorner = toQuadVector(topCorner, mapWidth, mapHeight, bounds);
		bottomCorner = toQuadVector(bottomCorner, mapWidth, mapHeight, bounds);

		FloatArray vertices = new FloatArray(new float[] {
				leftCorner.x, leftCorner.y,
				bottomCorner.x, bottomCorner.y,
				leftCorner.x, (float) bounds.getMinY(),

				bottomCorner.x, bottomCorner.y,
				rightCorner.x, (float) bounds.getMinY(),
				leftCorner.x, (float) bounds.getMinY(),

				bottomCorner.x, bottomCorner.y,
				rightCorner.x, rightCorner.y,
				rightCorner.x, (float) bounds.getMinY(), });
		// FloatArray vertices = new FloatArray(new float[] {
		// 0, 0, 0, resolution, resolution, 0 });
		ShortArray tris = triangulator.computeTriangles(vertices);
		PolygonRegion region = new PolygonRegion(world.getEngine().getDisplay().getShadowBackgroundTexture(), vertices.toArray(), tris.toArray());

		this.setComputedShadowRegion(region);
	}

	protected boolean hitWall = false;

	@Override
	protected void hitWall() {
		super.hitWall();
		hitWall = true;
	}

	private Vector2 toQuadVector(Vector2 vector2, int width, int height, Rectangle2D worldbounds) {
		float x = (float) (((vector2.x - worldbounds.getMinX()) / worldbounds.getWidth()) * width);
		float y = (float) (((vector2.y - worldbounds.getMinY()) / worldbounds.getHeight()) * height);
		x = Math.max(0, Math.min(x, width));
		y = Math.max(0, Math.min(y, height));

		return new Vector2(x, y);
	}

	public PolygonRegion getComputedShadowRegion() {
		return computedShadowRegion;
	}

	public boolean shouldComputeNewShadowRegion() {
		if(lastLocation == null)
			return true;
		if(computedShadowRegion == null)
			return true;

		return !this.location.equals(lastLocation) || this.relative_direction != this.lastRotation;
	}

	public double getEnergyIncome() {
		return energyIncome;
	}

	public double getEnergySpending() {
		return energySpending;
	}

	public int getGrowthStage() {
		int maxStages = (structure.getStructure().size() - 1) * 20;
		int stage = (int) (maturity * maxStages);

		if(stage > maxStages)
			stage = maxStages;

		return stage;
	}
}
