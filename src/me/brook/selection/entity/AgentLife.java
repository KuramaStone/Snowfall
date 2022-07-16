package me.brook.selection.entity;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import me.brook.neat.GeneticCarrier;
import me.brook.neat.network.NeatNetwork;
import me.brook.neat.network.NeatNetwork.NeatTransferFunction;
import me.brook.neat.network.Phenotype;
import me.brook.neat.randomizer.AsexualReproductionRandomizer;
import me.brook.neat.species.Species;
import me.brook.selection.World;
import me.brook.selection.entity.body.Hitbox.CellCollisionInfo;
import me.brook.selection.entity.body.Segment;
import me.brook.selection.entity.body.Structure;
import me.brook.selection.tools.Profiler;
import me.brook.selection.tools.Vector2;

public class AgentLife extends Agent {

	private static final long serialVersionUID = -8665240214194443508L;

	private static final int entitiesToTrack = 1;
	// sunlight, hue, relative x, relative y, energy, speed, nearby count, to light
	public static final int totalInputs = 12 + 6 * entitiesToTrack, outputs = 10;

	public AgentLife(World world, Species<Agent> parentSpecies, Color color, double speed, double size, Vector2 location, boolean makeBrain) {
		super(world, parentSpecies, color, speed, size, location, true);

		hasCollisionWithAgents = false;
		energyModifier = 1;
		canReproduce = true;
		canBeCarnivorous = true;
		canAge = true;
		// moveAtMaxiumum = true;

		if(makeBrain) {
			this.hasBrain = true;
			this.brain = createBrain();
		}
	}

	@Override
	public void init() {
		super.init();
		buildBody();
		this.hitbox = buildHitbox();
		updateSensorHormones();
	}

	public AgentLife() {
		super(null, null, null, 0, 0, null);
	}

	@Override
	protected boolean collideWithWall() {
		return true;
	}

	@Override
	public void tick(World world) {
		if(structure == null || hitbox == null) {
			return;
		}
		if(!shouldExist())
			return;
		
		super.tick(world);
		ejectFood();
		attack();
		heal();
		this.hitbox = buildHitbox();
		lastHealth = (float) health;
		hitWall = false;
		
	}

	@Override
	public void addEnergy(double energy) {
		super.addEnergy(energy);
		updateSensorHormones();
	}

	private void updateSensorHormones() {
		float photo = 1 / (1 + this.structure.getByType(Structure.PHOTO).size());
		float chems = 1 / (1 + this.structure.getByType(Structure.CHEMO).size());
		float hunter = 1 / (1 + this.structure.getByType(Structure.HUNT).size());
		float energy = (float) (1 / (1 + this.energy));

		Color c = new Color(Color.HSBtoRGB(hue, 1, 1));
		float r = c.getRed() / 255f;
		float g = c.getGreen() / 255f;
		float b = c.getBlue() / 255f;

		this.sensorHormones = buildHormones(1 - photo, 1 - chems, 1 - hunter, 1 - energy, r, g, b);
	}

	private void heal() {
		if(wantsToHeal > 0 && health < maxHealth && energy > 0) {
			// max heal speed of (size*metabolism) per second and 1 hp = 1000 energy
			double energyToUse = 1 * getMass() * 50;
			energyToUse = Math.min(energyToUse, this.energy);
			
			if(energyToUse == 0)
				return;
			
			addEnergy(-energyToUse);
			
			double add = energyToUse / getEnergyPerHP();
//			System.out.println(energyToUse + " " + add + " " + getMass() + " " + this.energy);
			
//			if(wantsToHeal > 0.5)
//				world.setSelectedEntity(this);

			this.health += add;
			if(health > maxHealth)
				health = maxHealth;
		}
	}

	private void attack() {
		if(!isAlive()) {
			return;
		}
		if(!isHunter())
			return;

		/*
		 * if wantsToAttack check if entity is an agent and if they're in position to be damaged
		 */
		boolean attacked = false;
		if(wantsToAttack) {

			for(Entity entity : collisionInfo.keySet()) {
				if(entity.isAlive()) {

					List<CellCollisionInfo> info = collisionInfo.get(entity);

					if(entity.isAgent()) {
						Agent prey = (Agent) entity;

						boolean attack = false;
						boolean isAttackingHunterCell = false;
						for(CellCollisionInfo cci : info) {

							if(cci.cell1.seg.getType().equals(Structure.HUNT))
								if(!cci.cell2.seg.getType().equals(Structure.THRUST)) {
									attack = true;
									if(cci.cell2.seg.getType().equals(Structure.HUNT))
										isAttackingHunterCell = true;
									break;
								}

						}

						int myCellCount = this.structure.getByType(Structure.HUNT).size();
						int preyCellCount = prey.structure.getByType(Structure.HUNT).size();

						if(attack) {

							double mass = getTotalMass();
							// size modifier
							double sizeMod = (this.getTotalMass() / (prey.getTotalMass() + this.getTotalMass()));
							double attackingAttackerMod = 1;
							
							// reduce damage when attacking a hunter cell
							if(isAttackingHunterCell) {
								// if hitting a hunter cell, compare total of hunter cells

								attackingAttackerMod = ((double) myCellCount) / (myCellCount + preyCellCount);
							}
							double damage = 1 * mass* myCellCount * sizeMod * attackingAttackerMod;

							
							if(damage == 0) {
								continue; 
							}

							double damageResult = prey.damage(this, damage);

							double energyGained = damageResult * getEnergyPerHP();
							boolean preyKilled = prey.getHealth() <= 0;
							if(preyKilled) { // instantly eat them entirely if they died from damage
								double preyEnergy = prey.getTotalBodyEnergy();
								energyGained += preyEnergy;

								// set values just to be safe
								prey.energy = 0;
								prey.setBodyMaintainanceEnergy(0);
								prey.willDropCorpse = false;
								prey.die(world, "eaten alive");
							}

							// calculate efficiency
							// energyGained *= getDietModifier(false);
							// energyGained *= 0.75;
							world.totalPreyGained += energyGained;

							EntityBite bite = new EntityBite(prey, energyGained);

							stomach.add(bite);
							attacked = true;
						}
					}
					else {
						boolean attack = false;
						for(CellCollisionInfo cci : info) {

							if(cci.cell1.seg.getType().equals(Structure.HUNT)) {
								attack = true;
								break;
							}
						}

						if(attack) {

							double damage = getTotalMass() * 0.25;
							damage *= 10; // increase eating of corpses since they're dead
							// size modifier
							damage *= this.structure.getByType(Structure.HUNT).size();

							double energyGained = damage * getEnergyPerHP();
							energyGained = Math.min(energyGained, entity.getEnergy());

							EntityBite bite = new EntityBite(entity, energyGained);
							stomach.add(bite);

							entity.addEnergy(-energyGained);
							// entity.die(world, "eaten while its dead because its a corpse so its not exactly alive but it was still eaten.");
							world.totalScavGained += energyGained;
							attacked = true;
						}
					}
				}
			}
		}

		this.isAttacking = attacked;

	}

	@Override
	public NeatNetwork createBrain() {
		List<NeatTransferFunction> possibleFunctions = Arrays.asList(new NeatNetwork.TanhFunction(), new NeatNetwork.RectifedLinearFunction(),
				new NeatNetwork.BinaryFunction(), new NeatNetwork.GaussianFunction(), new NeatNetwork.InverseFunction(),
				new NeatNetwork.LinearFunction(), new NeatNetwork.SigmoidFunction(),
				new NeatNetwork.SquareFunction());

		NeatNetwork brain = new NeatNetwork(getGlobalInnovationHistory(), 0, 1,
				new Phenotype(), true, possibleFunctions,
				totalInputs, outputs);

		// brain.connectAllLayers();
		// add quick and easy connections

		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(1).getNeuronID(), 0); // bias to move
		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(4).getNeuronID(), 1); // bias to light
		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(5).getNeuronID(), 1); // bias to chem
		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(7).getNeuronID(), 1); // bias to attack
		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(9).getNeuronID(), 1); // bias to digest
		brain.setWeightOf(brain.getLayers().get(0).get(totalInputs).getNeuronID(), brain.getLayers().get(1).get(3).getNeuronID(), 1); // bias to fuck

		brain.labelOutputs("rotate", "forceX", "forceY", "dtf", "light", "chemo", "heal", "attack", "hold", "digest");

		String[] entityLabels = new String[] { "r", "g", "b", "dist", "rot", "f value" };
		String[] inputs = new String[totalInputs];
		int index = 0;
		for(int i = 0; i < entitiesToTrack; i++) {
			for(int j = 0; j < entityLabels.length; j++)
				inputs[index++] = entityLabels[j] + "-" + i;
		}

		String[] otherLabels = new String[] { "light", "chemo", "speed x",
				"speed y", "age",
				"energy", "nearby", "to light", "wall", "shadows", "hurt", "murdering" };

		for(int i = 0; i < otherLabels.length; i++) {
			inputs[index++] = otherLabels[i];
		}

		brain.labelInputs(inputs);

		return brain;
	}

	@Override
	public void useBrainOutput() {
		super.useBrainOutput();
	}

	@Override
	public void die(World world, String reason) {
		super.die(world, reason);
		setTimeOfDeath(System.currentTimeMillis());
	}

	int ageOfLastCalc = -1;

	@Override
	public void calculateOutputs() {
		if(!isAlive()) {
			return;
		}
		ageOfLastCalc = age;
		if(!hasBrain) {
			return;
		}
		if(brain == null) {
			return;
		}
		if(!this.shouldExist())
			return;

		// if(age == ageOfLastCalc) {
		// System.out.println("Calculated twice in one tick.");
		// }

		Vector2 location = getLocation();

		// double toSurface = Math.abs(location.y - world.getBorders().getBounds().getMaxY()) + 50;
		// double toFloor = Math.abs(location.y - world.getBorders().getBounds().getMinY()) + 50;

		this.entitiesBlockingLightFactor = 0;
		this.entitiesBlockingChemsFactor = 0;
		calculateNearbyEntities();

		double[] inputs = new double[brain.getInputNeurons().size()];

		int index = 0;
		for(int i = 0; i < this.lastNearbyEntities.size(); i++) {
			NearbyEntry closest = this.lastNearbyEntities.get(i);

			if(closest != null) {
				if(attachedEntities.contains(closest.entity)) {
					continue;
				}

				Vector2 relClosest = closest.entity.getLocation().copy();
				// translate and rotate relative to agent
				relClosest = relClosest.subtract(location).rotate(new Vector2(), -this.getRelativeDirection());

				Color c = closest.entity.getColor();

				if(!closest.entity.shouldExist()) { // check after values are locked
					continue;
				}

				inputs[index++] = c.getRed() / 255.0;
				inputs[index++] = c.getGreen() / 255.0;
				inputs[index++] = c.getBlue() / 255.0;
				inputs[index++] = 1 / (1 + relClosest.distanceToSq(this.location));
				inputs[index++] = relativeAngleTo(this.relative_direction, closest.entity.relative_direction); // show entity's direction relative to our own
				inputs[index++] = 1.0 / (1 + closest.entity.getEnergy() / 10000.0);

			}
			else {
				inputs[index++] = 0; // r
				inputs[index++] = 0; // g
				inputs[index++] = 0; // b
				inputs[index++] = 0;
				inputs[index++] = 0;
				inputs[index++] = 1;
			}

			break;
		}

		boolean isFacingWall = true;

		for(int step = 0; step < 2; step++) {
			// step forward to see if the wall intersects that point until sight_range units away
			if(world.getBorders().intersects(this.location.add(new Vector2(this.relative_direction).multiply(this.sight_range * (step / 4.0))))) {
				isFacingWall = true;
				break;
			}
		}
		this.lastShadowValue = world.getShadowsAt(this.location);

		Vector2 relVelocity = this.velocity.rotate(new Vector2(), this.relative_direction);

		inputs[index++] = world.getLightAt(location); // 6
		inputs[index++] = world.getChemicalAt(location);
		inputs[index++] = 1 / (1 + relVelocity.x);
		inputs[index++] = 1 / (1 + relVelocity.y);
		inputs[index++] = (this.getMaturity() / 2.0);
		inputs[index++] = 1.0 / (1 + this.getEnergy());
		inputs[index++] = 1.0 / (1 + lastNearbyEntities.size());
		inputs[index++] = relativeAngleTo(Math.PI / 2, relative_direction); // to light is just their direction relative to up
		inputs[index++] = hitWall ? 1 : 0; // world.getFoodNoise(getLocation().x, getLocation().y);
		inputs[index++] = lastShadowValue; // shadow value
		inputs[index++] = 1.0 / (1 + getTicksSinceLastHurt()); // hurting
		inputs[index++] = isAttacking ? 1 : 0; // hurting

		// float[] sumOfNearbyHormones = new float[this.sensorHormones.length];
		//
		// int foundHormones = 0;
		// for(NearbyEntry near : this.lastNearbyEntities) {
		// if(near != null && near.entity != null && near.entity.sensorHormones != null) {
		//
		// for(int i = 0; i < sumOfNearbyHormones.length; i++) {
		// double dist = near.entity.location.distanceToRaw(this.location);
		// sumOfNearbyHormones[i] += near.entity.sensorHormones[i] / (1 + dist);
		// }
		//
		// }
		// }
		//
		// for(int i = 0; i < sensorHormones.length; i++) {
		// inputs[index++] = sensorHormones[i];
		// }

		// send inputs to brain
		brain.setInputs(inputs);
		Profiler.startRecord("calculate");
		brain.calculate();
		Profiler.stopRecord("calculate");

		double[] out = brain.getOutput();

		// check if any values could be out of range for possible input errors
		for(int i = 0; i < inputs.length; i++) {
			if(Double.isInfinite(inputs[i]) || Double.isNaN(inputs[i])) {
				System.out.println("Potential input error. One or more inputs is NaN");
				System.out.println(i + ": " + brain.getLayers().get(0).get(i).getLabel() + ", " + inputs[i]);
				getAge();
			}
		}

		lastOutputs = out;
	}

	private void calculateNearbyEntities() {
		List<NearbyEntry> nearby = new ArrayList<Agent.NearbyEntry>();

		for(Entity e : world.getNearbyEntities(this.location, this.sight_range)) {
			if(e != null && e != this) {
				float dist = e.getLocation().distanceToRaw(this.location);
				double angle = relativeAngleTo(this.getLocation(), e.getLocation());
				nearby.add(new NearbyEntry(e, new EntityViewInfo(dist, angle, e)));
			}
		}

		nearby.sort(new DistanceToOriginSorter());
		if(!nearby.isEmpty())
			this.closestEntity = nearby.get(0).entity;
		else
			this.closestEntity = null;

		this.lastNearbyEntities = nearby;
	}

	public int getAgeOfLastCalc() {
		return ageOfLastCalc;
	}

	public static class EntryKey implements Entry<Entity, EntityViewInfo> {

		private Entity e;
		private EntityViewInfo view;

		public EntryKey(Entity e, EntityViewInfo view) {
			this.e = e;
			this.view = view;
		}

		@Override
		public Entity getKey() {
			return e;
		}

		@Override
		public EntityViewInfo getValue() {
			return view;
		}

		@Override
		public EntityViewInfo setValue(EntityViewInfo value) {
			return null;
		}

	}

	private double sigmoid(double x, double d) {
		return 1 / (1 + Math.pow(Math.E, -x * d));
	}

	protected static double adjustAngleRelativeTo(double angle, double relativeTo) {

		// THIS USES DEGREES SO WE HAVE TO CONVERT
		angle = Math.toDegrees(angle);
		relativeTo = Math.toDegrees(relativeTo);

		double d = angle - relativeTo; // -1 - -44 = -1 + 44

		double e = Math.abs(relativeTo) + Math.abs(angle - 360);

		double value = Math.min(d, e);

		// convert back to radians
		return Math.toRadians(value);
	}

	protected boolean canSee(Entity near) {
		return true;
	}

	@Override
	public GeneticCarrier createClone() {
		// new agent instance
		AgentLife agent = new AgentLife(world, parentSpecies, color, 1, 1, getLocation().copy(), false);
		try {
			// copy brain over
			agent.brain = this.brain.copy();
			agent.bodyDNA = String.valueOf(this.bodyDNA);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		// reset to apply phenotype
		agent.reset();

		return agent;
	}

	@Override
	public double[] calculateNoveltyMetrics() {
		return new double[] { energyConsumed / world.getFoodValue() };
	}

	@Override
	public double calculateFitness() {
		double fitness = energyConsumed;// * (Math.log(children + 1) + 1);

		setFitness(fitness);

		return getFitness();
	}

	public void ejectFood() {
//		if(wantsToEjectFood <= 2)
//			return;
//
//		double energyToEject = (getMass() * size) * wantsToEjectFood;
//
//		Vector2 spawnAt = this.getLocation().add(new Vector2(this.relative_direction).multiply(size));
//		if(!world.getBorders().contains(spawnAt.x, spawnAt.y)) {
//			return;
//		}
//
//		energyToEject = Math.min(this.getEnergy(), energyToEject); // cant eject more than remaining energy
//
//		// dont allow too small ejections. thousands of 1 energy dots sounds lame
//		if(energyToEject < 5000) {
//			return;
//		}
//
//		this.addEnergy(-energyToEject);
//		// reduce actual energy in pellet. a bit of waste to make it not 100% efficient
//		energyToEject *= 0.5;
//
//		Corpse corpse = new Corpse(world, energyToEject, spawnAt);
//		world.addEntity(corpse);
//		// add ejection speed to corpse
//		corpse.applyForce(this.relative_direction, getMass() * 10);
	}

	private double lastShadowValue;

	@Override
	protected void photosynthesis() {
		if(lastNearbyEntities == null)
			return;
		int nearbySize = 0;
		for(NearbyEntry ne : lastNearbyEntities)
			if(ne.getKey().isAgent() && ne.getKey().getLocation().distanceToSq(this.location) < sight_range &&
					((Agent) ne.getKey()).wantsToPhotosynthesize)
				nearbySize++;

		// double competition = Math.max(0, Math.min(1, (size - entitiesBlockingLightFactor) / size));
		double intensity = getLightReceived();
		double competition = Math.max(0, Math.min(1, (1.0 / (1 + Math.pow(nearbySize, 1))))); // nearby agents reduce light for this agent
		this.lastLightExposure = (float) intensity;

		double gain = 100 * intensity;
		gain *= competition;
		// gain *= world.getSkillFactor();

		// if(isSelected())
		// System.out.println(gain);

		world.totalLightGained += gain;
		addEnergy(gain);
		sunGained += gain;
	}

	protected void chemosynthesis() {
		if(lastNearbyEntities == null)
			return;

		int nearbySize = 0;
		for(NearbyEntry ne : lastNearbyEntities)
			if(ne.getKey().isAgent() && ne.getKey().getLocation().distanceToSq(this.location) < sight_range &&
					((Agent) ne.getKey()).wantsToChemosynthesize)
				nearbySize++;
		double competition = Math.max(0, Math.min(1, (1.0 / (1 + Math.pow(nearbySize, 2))))); // nearby agents reduce light for this agent
		double intensity = getChemsReceived();

		double gain = 50 * intensity;
		gain *= competition;
		// gain *= world.getSkillFactor();
		
		if(isSelected())
			getAge();

		world.totalChemsGained += gain;
		addEnergy(gain);
		chemGained += gain;
		this.lastChemicalExposure = (float) getChemsReceived();
		
	}

	private double getLightReceived() {

		float totalLight = 0;

		for(Segment seg : structure.getByType(Structure.PHOTO)) {

			// get light received at location of each cell
			Vector2 loc = hitbox.getRotatedLow(new Vector2(), 0); // get bottom left of structure
			loc = loc.add(seg.getPosition().add(structure.getCoreOffset()).add(0.5, 0.5).multiply(Agent.getSizeOfSegment())); // get structure spot
			loc = loc.rotate(hitbox.getOrigin(), hitbox.getCurrentTheta()); // rotate it into position

			float lightNormals = getSurfaceAreaForSegment(seg, new Vector2(0, -1));
			float sunlight = (float) (world.getShadowsAt(loc) * world.getLightAt(loc));

			// if(isSelected())
			// System.out.println(lightNormals);

			totalLight += lightNormals * sunlight;
		}

		return totalLight;
	}

	private double getChemsReceived() {

		float totalLight = 0;

		for(Segment seg : structure.getByType(Structure.CHEMO)) {

			// get light received at location of each cell
			Vector2 loc = hitbox.getRotatedLow(new Vector2(), 0); // get bottom left of structure
			loc = loc.add(seg.getPosition().add(structure.getCoreOffset()).add(0.5, 0.5).multiply(Agent.getSizeOfSegment())); // get structure spot
			loc = loc.rotate(hitbox.getOrigin(), hitbox.getCurrentTheta()); // rotate it into position

			float normals = getSurfaceAreaForSegment(seg, new Vector2(0, 1));
			float chems = (float) (world.getChemicalAt(loc));

			// if(isSelected())
			// System.out.println(lightNormals);

			totalLight += normals * chems;
		}

		return totalLight;
	}

	@Override
	public void mutate(float multiplier) {
		double geneMultiplier = this.geneMultiplier * 0.5;
		double geneFactor = this.geneFactor;

		brain.mutateNetwork(
				0.2 * geneMultiplier, // add conn
				0 * geneMultiplier, // toggle conn
				0.05 * geneMultiplier, // delete conn
				0.02 * geneMultiplier, // new node
				0.02 * geneMultiplier, // del node
				new AsexualReproductionRandomizer(0.1 * geneMultiplier, 0.1 * geneFactor),
				0.1 * geneFactor, // activation magnitude
				0.05 * geneMultiplier, // activation chance
				0.2 * geneMultiplier); // activation value change
		brain.getPhenotype().mutate(0.15 * geneMultiplier);

		this.bodyDNA = mutateBodyDNA(this.bodyDNA, 
				0.01 * geneMultiplier, // node type chance
				0.02 * geneMultiplier, // direction add chance
				0.03 * geneMultiplier, //  direction del
				0.01 * geneMultiplier, // node copy
				0.02 * geneMultiplier, // node delete
				0.01 * geneMultiplier); // angle 

		// if(random.nextDouble() < rate)
		// brain.mutateNeuronsLimited(1, 1, 3, 0, 0, 0, 0);

		if(multiplier > 1) {
			mutate(multiplier - 1);
			return;
		}

		adjustForPhenotype();
	}

	public void setBodyGene(String bodyGene) {
		this.bodyDNA = bodyGene;
	}

}
