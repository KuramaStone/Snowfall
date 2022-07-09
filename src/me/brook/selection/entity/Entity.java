package me.brook.selection.entity;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import me.brook.neat.network.NeatNetwork;
import me.brook.selection.LibDisplay;
import me.brook.selection.World;
import me.brook.selection.entity.body.Hitbox.CellCollisionInfo;
import me.brook.selection.tools.QuadSortable;
import me.brook.selection.tools.SpatialHashing.Coords;
import me.brook.selection.tools.Vector2;

public abstract class Entity implements QuadSortable, Comparable<Entity> {

	private final UUID uuid = UUID.randomUUID();

	private boolean alive = true;
	protected String reasonForDeath;

	protected double energy;
	protected double size;

	protected transient World world;
	protected Vector2 location;
	protected transient Color color;
	protected String iconID;
	protected transient Coords lastCoords;
	protected int age;

	private double digestionDifficulty = 0;

	protected double relative_direction; // in radians
	protected Vector2 velocity;
	protected Vector2 acceleration;
	protected float nextRelativeDirection;

	// protected transient SimulationBody body;

	protected boolean willDropCorpse = true;

	protected List<Entity> attachedEntities;
	protected transient Entity attachedTo;
	private String attachedUUID;
	private Vector2 relativeAttachment;
	protected float[] sensorHormones;
	private boolean disposed = false;

	public Entity(World world, double energy, Vector2 location, Color color, double size) {
		this.world = world;
		this.energy = energy;
		this.location = location;
		this.color = color;
		this.size = size;

		this.acceleration = new Vector2();
		this.velocity = new Vector2();
		attachedEntities = Collections.synchronizedList(new ArrayList<>());

	}

	public void tick(World world) {
		// if(!this.velocity.isFinite()) {
		// die(world, "violating my laws of conservation");
		// return;
		// }

		if(attachedTo != null) {
			if(!attachedTo.isAlive()) {
				detach();
			}
		}

		if(alive) {
			moveWithCurrent();
			move();
			// dyn4j();

			age++;
			if(isSelected())
				getAge();
		}

	}

	public void move() {
		if(!isAlive()) {
			return;
		}

		// don't move if attached
		if(attachedTo != null)
			return;

		// acceleration = new Vector2(0, 5000);
		Vector2 tempVel = velocity.add(acceleration);

		if(!tempVel.isFinite()) {
			die(world, "violating my laws of conservation");
			return;
		}

		double smoothness = 1;
		double windResistance = 1.0 / (1 + getSurfaceArea(tempVel.atan2(new Vector2())) * smoothness); // slow down with bigger surface area
		// decrease acceleration according to how much it is decreased
		windResistance = windResistance / (1 + Math.pow(tempVel.distanceToSq(new Vector2()), 2)); // slow down as speed increases
		this.velocity = velocity.add(acceleration.multiply(windResistance));
		acceleration = acceleration.multiply(0);

		Vector2 temp = clampToWorldBorders(this.location.add(velocity));

		Map<Entity, List<CellCollisionInfo>> results = validate(temp, nextRelativeDirection);

		if(resolveCollisions(results)) {

			if(!intersectsWall(temp, nextRelativeDirection)) {
				setLocation(temp);

				this.relative_direction = this.nextRelativeDirection;
			}

			for(Entity ent : new HashSet<>(attachedEntities)) {
				ent.moveWithAttachedParent();
			}
		}

	}

	protected void stuckInWall() {

	}

	protected boolean intersectsWall(Vector2 temp, float nextRelativeDirection2) {
		return world.getBorders().intersects(temp);
	}

	public double getTotalMass() {
		return getMass() + attachedMass();
	}

	public double attachedMass() {
		double mass = 0;
		if(attachedEntities != null)
			for(Entity ent : this.attachedEntities) {
				mass += ent.getMass();
			}
		return mass;
	}

	public void moveWithAttachedParent() {
		if(attachedTo == null)
			return;

		Vector2 newLoc = attachedTo.location.add(relativeAttachment);
		this.setLocation(newLoc);
	}

	protected float getSurfaceArea(float theta) {
		return (float) size; // assume a sphere
	}

	protected boolean resolveCollisions(Map<Entity, List<CellCollisionInfo>> results) {
		return true;
	}

	protected Map<Entity, List<CellCollisionInfo>> validate(Vector2 temp, float rotation) {
		return null;
	}

	protected void applyForce(double theta, double force) {
		this.applyForce(new Vector2(theta).multiply(force / getTotalMass()));

	}

	private Vector2 clampToWorldBorders(Vector2 vector) {
		vector = vector.copy();

		Rectangle2D bounds = world.getBorders().getBounds2D();
		if(vector.x > bounds.getMaxX()) {
			vector.x = (float) bounds.getMaxX();
		}
		if(vector.y > bounds.getMaxY()) {
			vector.y = (float) bounds.getMaxY();
		}
		if(vector.x < bounds.getMinX()) {
			vector.x = (float) bounds.getMinX();
		}
		if(vector.y < bounds.getMinY()) {
			vector.y = (float) bounds.getMinY();
		}

		return vector;
	}

	public boolean isCorpse() {
		return false;
	}

	public double getMass() {
		return size * size;
	}

	protected void applyImpulse(Vector2 force) {
		Vector2 loc = clampToWorldBorders(this.location.add(force.divide(getTotalMass())));
		if(!world.getBorders().intersects(loc))
			setLocation(loc);
	}

	protected void applyForce(Vector2 force) {
		acceleration = acceleration.add(force.divide(getTotalMass()));
	}

	private double clampAngle(double angle) {
		if(angle > Math.PI)
			angle -= 2 * Math.PI;
		else if(angle < -Math.PI)
			angle += 2 * Math.PI;

		return angle;
	}

	protected double getCurrentVelocity() {
		return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
	}

	public Entity() {
	}

	public boolean isMeat() {
		return this instanceof Meat;
	}

	public boolean isBerry() {
		return this instanceof Berry;
	}

	public boolean isAlive() {
		return alive;
	}

	public void setAlive(boolean isAlive) {

		if(reasonForDeath == null || reasonForDeath.isEmpty()) {
			if(!isAlive)
				this.alive = isAlive;
		}

		this.alive = isAlive;
	}

	public boolean canEat(Entity target) {
		return true;
	}

	public double getSize() {
		return size;
	}

	public void setSize(double size) {
		this.size = size;
	}

	public double getEnergy() {
		return energy;
	}

	public void setEnergy(int energy) {
		this.energy = energy;
	}

	public Vector2 getLocation() {
		return location;
	}

	public void setLocation(Vector2 location) {
		this.location = location;

		// if(body != null) {
		// body.translateToOrigin();
		// body.translate(location.toDyn4j());
		// }
	}

	public World getWorld() {
		return world;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void die(World world, String reason) {
		this.reasonForDeath = reason;
		setAlive(false);
		this.detach();
		this.detachAll();
		world.removeEntity(this);
	}

	public void detachAll() {
		new HashSet<Entity>(this.attachedEntities).forEach(e -> e.detach());
		this.attachedEntities.clear();
	}

	protected void moveWithCurrent() {
		double currentTheta = world.getCurrentThetaAt(this.location);
		double currentForce = 10;

		if(this.isAgent())
			this.applyForce(currentTheta, currentForce * 0);
		else
			this.applyForce(currentTheta, currentForce);
	}

	public boolean isAgent() {
		return false;
	}

	public void setIconID(String iconID) {
		this.iconID = iconID;
	}

	public String getIconID() {
		return iconID;
	}

	public UUID getUUID() {
		return uuid;
	}

	public boolean isEdible() {
		return true;
	}

	public double getRelativeDirection() {
		return 0;
	}

	public Shape getShape() {
		return null;
	}

	public int getChunkX() {
		return (int) (location.x / 1000);
	}

	public int getChunkY() {
		return (int) (location.y / 1000);
	}

	public Vector2 getVelocity() {
		return velocity;
	}

	public void setVelocity(Vector2 velocity) {
		this.velocity = velocity;
	}

	public boolean isNaturalMeat() {
		return this instanceof Meat;
	}

	public String getReasonForDeath() {
		return reasonForDeath;
	}

	public Coords getLastCoords() {
		return lastCoords;
	}

	public void setLastCoords(Coords lastCoords) {
		this.lastCoords = lastCoords;
	}

	public double getDigestionDifficulty() {
		return digestionDifficulty;
	}

	public void setDigestionDifficulty(double digestionDifficulty) {
		this.digestionDifficulty = digestionDifficulty;
	}

	public static class SaveState implements Serializable {
		private static final long serialVersionUID = 9150081693676342841L;
		public java.util.Map<String, Object> map = new HashMap<>();
	}

	public static Entity load(World world, SaveState state) {

		Entity entity = null;

		Vector2 location = (Vector2) state.map.get("location");
		double energy = (double) state.map.get("energy");
		int age = (int) state.map.get("age");
		String classname = (String) state.map.get("class");
		Vector2 velocity = (Vector2) state.map.get("velocity");
		String attachedUUID = (String) state.map.get("attachedTo");

		if(classname.equals("AgentLife")) {
			double rotation = (double) state.map.get("rotation");
			NeatNetwork brain = (NeatNetwork) state.map.get("brain");
			int generation = (int) state.map.get("generations");
			int children = (int) state.map.get("children");
			double bodyEnergy = (double) state.map.get("bodyEnergy");
			String bodyGene = (String) state.map.get("bodyGene");

			AgentLife life = new AgentLife(world, null, null, 0, 0, new Vector2(), false);
			life.setRelativeDirection(rotation);
			life.setBrain(brain);
			life.generation = generation;
			life.children = children;
			life.setBodyMaintainanceEnergy(bodyEnergy);
			life.setBodyGene(bodyGene);

			entity = life;
		}
		else if(classname.equals("Corpse")) {
			entity = new Corpse(world, energy, location);
		}

		entity.setLocation(location);
		entity.energy = energy;
		entity.age = age;
		entity.velocity = velocity;
		entity.attachedUUID = attachedUUID;

		return entity;

	}

	public void setAge(int age) {
		this.age = age;
	}

	public static Entity load(World world, ThreadLocal<Kryo> kryoLocal, String filePath) throws Exception {

		try {
			File file = new File(filePath);
			if(!file.exists()) {
				throw new FileNotFoundException("Cannot find file: " + filePath);
			}

			Input input = new Input(new FileInputStream(filePath));

			Object ob = kryoLocal.get().readClassAndObject(input);
			input.close();

			SaveState state = (SaveState) ob;

			Entity entity = null;

			Vector2 location = (Vector2) state.map.get("location");
			double energy = (double) state.map.get("energy");
			int age = (int) state.map.get("age");
			String classname = (String) state.map.get("class");

			if(classname.equals("AgentLife")) {
				double rotation = (double) state.map.get("rotation");
				NeatNetwork brain = (NeatNetwork) state.map.get("brain");
				int generation = (int) state.map.get("generations");
				int children = (int) state.map.get("children");

				AgentLife life = new AgentLife(world, null, null, 0, 0, new Vector2(), false);
				life.setRelativeDirection(rotation);
				life.setBrain(brain);
				life.generation = generation;
				life.children = children;

				entity = life;
			}
			else if(classname.equals("Corpse")) {
				entity = new Corpse(world, energy, location);
			}

			entity.setLocation(location);
			entity.energy = energy;
			entity.age = age;

			return entity;

		}
		catch(IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}

		return null;
	}

	public int getAge() {
		return age;
	}

	public boolean isSelected() {
		return this == world.getSelectedEntity();
	}

	public SaveState createSaveState() {
		SaveState state = new SaveState();
		state.map.put("location", this.location);
		state.map.put("energy", this.energy);
		state.map.put("class", this.getClass().getSimpleName());
		state.map.put("age", age);
		state.map.put("velocity", velocity); // save as my vector for serialized
		if(attachedTo != null && attachedTo.isAlive())
			state.map.put("attachedTo", attachedTo.getUUID().toString());

		return state;
	}

	public void save(ThreadLocal<Kryo> kryoLocal, String absolutePath) throws Exception {
		FileOutputStream fos = new FileOutputStream(new File(absolutePath));
		Output output = new Output(fos);
		kryoLocal.get().writeClassAndObject(output, createSaveState());

		output.close();
		output.flush();
		fos.close();
	}

	public void addEnergy(double energy) {
		// if(this.isSelected())
		// System.out.println(energy);
		this.energy += energy;
	}

	public void setEnergy(double energy) {
		this.energy = energy;
	}

	public double getTotalBodyEnergy() {
		return this.energy;
	}

	@Override
	public int compareTo(Entity o) {
		return (int) Math.signum(o.age - this.age);
	}

	public void buildBody() {
		// body = new SimulationBody();
		// body.addFixture(Geometry.createCircle(size / 2));
		// body.setMass(MassType.NORMAL);
		// body.setLabel("");
		// body.setGravityScale(0);
		// if(velocity != null) {
		// body.setLinearVelocity(velocity.toDyn4j());
		// }
		//
		// body.translate(this.location.toDyn4j());
	}

	public void attachTo(Entity entity) {
		this.attachedTo = entity;
		this.relativeAttachment = this.location.subtract(entity.location);
		if(relativeAttachment == null)
			System.exit(1);
		entity.addAttachedEntity(this);
	}

	public void detach() {
		if(attachedTo != null) {
			this.attachedTo.removeAttachedEntity(this);
			this.attachedTo = null;
		}
	}

	public void removeAttachedEntity(Entity entity) {
		attachedEntities.remove(entity);
	}

	public void addAttachedEntity(Entity entity) {
		attachedEntities.add(entity);
	}

	public void init() {

		// get entity from attacheduuid
		if(attachedUUID != null) {
			Entity entity = world.getEntityByUUID(UUID.fromString(attachedUUID));
			if(entity != null && entity.isAlive()) {
				attachTo(entity);
				attachedUUID = null;
			}
		}

	}

	public Entity getAttachedTo() {
		return attachedTo;
	}

	public float[] getSensorHormones() {
		return sensorHormones;
	}

	public List<Entity> getAttachedEntities() {
		return attachedEntities;
	}

	// public SimulationBody getBody() {
	// return body;
	// }

	protected float[] buildHormones(float photo, float chems, float hunter, float energy, float r, float g, float b) {
		return new float[] { photo, chems, hunter, energy, r, g, b };
	}

	public void dispose() {
		disposed = true;

		attachedEntities = null;
	}

	public boolean isValid() {
		return !disposed;
	}

}
