package me.brook.selection;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import me.brook.neat.GeneticCarrier;
import me.brook.neat.network.NoveltyTracker;
import me.brook.neat.network.Phenotype.Pheno;
import me.brook.neat.species.InnovationHistory;
import me.brook.neat.species.Species;
import me.brook.neat.species.SpeciesManager;
import me.brook.selection.LibDisplay.RenderingMode;
import me.brook.selection.LibDisplay.TextureTracker;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.AgentLife;
import me.brook.selection.entity.Berry;
import me.brook.selection.entity.Corpse;
import me.brook.selection.entity.Entity;
import me.brook.selection.entity.Entity.SaveState;
import me.brook.selection.entity.Meat;
import me.brook.selection.tools.Border;
import me.brook.selection.tools.Noise;
import me.brook.selection.tools.Profiler;
import me.brook.selection.tools.SpatialHashing;
import me.brook.selection.tools.Tracker;
import me.brook.selection.tools.Vector2;
import me.brook.selection.tools.hash.PSHOffsetTable;

public class World {

	private static final DecimalFormat df = new DecimalFormat("##.000000");

	private Border borders;

	private Engine engine;

	private ArrayList<Entity> entities;
	private SpatialHashing<Entity> hashing;
	private PSHOffsetTable table;

	private SpeciesManager<Agent> speciesManager;

	private int currentGeneration;
	private int lastMedianAge;

	private boolean realTime = true;
	private long startTimeFromSave;

	private boolean spawnBerries = true;
	private double foodValue = 15000;
	private double berryDensity = 0.0000002 * 0;
	private double skillFactor = 1;
	private Noise foodNoise, waterCurrents;
	private double noiseOffset;

	private int heatMapScale = 1000;
	private double worldWidth = 8000;
	private double worldHeight = worldWidth * (9.0 / 16);
	private int polygonSides = 4;
	private int wallsInBorders = 0;

	private long forceKillTime = 3000;

	private Random random;

	private List<Species<Agent>> species;
	private List<java.lang.Double> fitnessOverTime;

	private List<Entity> scheduleAdditions, scheduledRemovals;

	private Vector2 cursorEntity;

	private int minimumPopulation = 500, maxPopulation = 3000;
	int[] relativePopulation;

	private int selectedPopulation = -1;
	private Entity selectedEntity;

	private boolean forceNextGeneration = false;

	private static InnovationHistory innovationHistory = new InnovationHistory();
	private NoveltyTracker<Agent> noveltyTracker;

	private int ticksSinceRestart = 0;
	private boolean wasLoadedFromSave = false;

	private Texture displayGraph;

	private Tracker<Double> tracker;
	// total energies for tracking purposes
	public double totalEnergyUsed, totalLightGained, totalChemsGained, totalPreyGained, totalScavGained;

	private List<CalculateTask> threads;
	private ConcurrentLinkedQueue<Entity> threadEntities;
	private boolean[] threadsFinished = new boolean[getThreadCount()];
	private int seed;

	private int lastLivingPopulation;

	public World(Engine engine, String startingSavePath, boolean mutate) throws FileNotFoundException {
		df.setGroupingSize(3);
		df.setGroupingUsed(true);
		toDispose = new ArrayList<>();
		noveltyTracker = new NoveltyTracker<Agent>(0.01);
		noveltyTracker.setAllowRepeats(false);
		random = new Random();
		foodNoise = new Noise(4, 0.0025 / ((worldHeight) / heatMapScale), 0.5f, 2, random.nextInt(100000), 0);
		waterCurrents = new Noise(3, 1 / worldHeight, 0.5f, 2, 7777777, 0);
		createPhysicsWorld();
		borders = createBorder(polygonSides, worldWidth, worldHeight);
		tracker = new Tracker<Double>();

		this.engine = engine;
		entities = new ArrayList<Entity>();
		fitnessOverTime = new ArrayList<>();
		scheduleAdditions = new ArrayList<>();
		scheduledRemovals = new ArrayList<Entity>();
		this.seed = random.nextInt(1000000);

		// others.add(new Predator(new Vector2(100, 100), 300));

		if(startingSavePath != null) {
			loadWorldSave(startingSavePath);
		}
		if(speciesManager == null) { // nothing here???
			Agent agent = createDefaultAgent();

			speciesManager = new SpeciesManager<Agent>(1, 10, agent);
			speciesManager.fillAllWithReps(minimumPopulation, true, 1);
			speciesManager.getAllGeneticCarriers().forEach(e -> addEntity(e));
		}

		// speciesManager.applyRandomizerToAll(new BoundedRandomizer(5));

		this.species = new ArrayList<>(speciesManager.getSpecies().values());
		speciesManager.setAllowSpeciation(true);
		speciesManager.setSpeciationFactors(1, 0.3, 5, 1.0);
		speciesManager.checkForSpeciation(true);

		this.species = new ArrayList<>(speciesManager.getSpecies().values());
		relativePopulation = new int[this.species.size()];

		for(int i = 0; i < species.size(); i++) {
			relativePopulation[i] = species.get(i).size();
		}

		Rectangle2D bounds = borders.getBounds2D();
		float[] worldData = new float[] {
				(float) bounds.getMinX(), (float) bounds.getMinY(),
				(float) bounds.getMaxX(), (float) bounds.getMaxX(),
				(float) bounds.getWidth(), (float) bounds.getHeight(),
				getLivingPopulation(), ticksSinceRestart
		};

	}

	public void configureAllAgents() {

		float index = 0;
		for(Species<Agent> sp : speciesManager.getSpecies().values()) {

			for(Agent e : sp) {

				configureNewAgent(e, index);
				index++;
			}

		}

	}

	private void createPhysicsWorld() {
		// dyn4 = new org.dyn4j.world.World<>();
		// dyn4.setGravity(0, -9.8);
	}

	private Agent createDefaultAgent() {
		double size = 10;

		Agent agent = new AgentLife(this, null, Color.BLUE, 4, size, new Vector2(), true);
		agent.reset();
		agent.setActive(true);

		return agent;
	}

	public void adjustSkillFactor() {
		int living = lastLivingPopulation;
		double c = 100;
		skillFactor = skillFactor * Math.exp((maxPopulation - living) / (maxPopulation * c));

		if(skillFactor > 1)
			skillFactor = 1;

		// System.out.println(skillFactor);
	}
	
	public boolean isWaitingForResults() {
		return updateResults != null && !areResultsReady();
	}

	public boolean update() {
		if(isWaitingForResults())
			return false;
		
		lastLivingPopulation = calculateLivingPopulation();

		// make sure to save after threads are guaranteed to be finished
		if(this.ticksSinceRestart != 0 && this.ticksSinceRestart % 25000 == 0 && this.ticksSinceRestart != startTimeFromSave) {
			save(true);
		}

		entities.removeIf(e -> e == null);
		scheduledDisposes();
		createSpatialHash();
		updateSun();

		// System.out.println(String.format("%s %s %s %s %s", this.entities.size(),
		// this.speciesManager.getAllGeneticCarriers(true).size(), this.hashing.getAll().size(),
		// this.hashing.getChunks().size(), this.speciesManager.getSpecies().size()));

		ticksSinceRestart++;

		focusOnSelectedAgent();

		checkAdditionsRemovals();
		adjustSkillFactor();

		if(engine.getRenderingMode() != RenderingMode.ENTITIES) {
			if(this.toBuild.size() > lastLivingPopulation * 1.1) {
				System.err.println("toBuild array too full.");
				for(int i = 0; i < toBuild.size(); i++) {
					Agent a = toBuild.get(i);
					if(a == null || toBuild.contains(a)) {
						toBuild.remove(i);
						i--;
						continue;
					}

					if(a.isAlive() && a.shouldExist()) {
						// do nothing
					}
					else {
						toBuild.remove(a);
						i--;
					}
				}
			}
		}

		if(realTime) {
			if((this.ticksSinceRestart) % 500 == 0) {

				speciesManager.getSpecies().values().forEach(s -> s.removeNull());
				speciesManager.trim();
				trimEntities();

				engine.getDisplay().checkDisposal();

				// System.out.println(Profiler.writeProfileData());
				Profiler.resetProfileData();

				Agent best = null;
				double highest = Double.MIN_VALUE;
				for(Species<Agent> sp : this.species) {
					sp.trimDeadEntities(0);
					for(Agent a : sp) {

						double h = a.getFitness();

						if(best == null || h > highest) {
							best = a;
							highest = h;
						}
					}
				}

				if(selectedEntity != null && selectedEntity.isAgent())
					setBestBrainImage((Agent) selectedEntity, engine.getDisplay().getImageWidth(),
							engine.getDisplay().getImageHeight());
				else if(best != null)
					setBestBrainImage(best, engine.getDisplay().getImageWidth(), engine.getDisplay().getImageHeight());

				tracker.addData("photosynthesis", Double.valueOf(this.totalLightGained));
				tracker.addData("chemosynthesis", Double.valueOf(this.totalChemsGained));
				tracker.addData("predation", Double.valueOf(this.totalPreyGained));
				tracker.addData("scavenged", Double.valueOf(this.totalScavGained));
				tracker.addData("fps", Double.valueOf(engine.getAverageFps()));
				createGraphs();

			}

		}
		else {
			if(ticksSinceRestart >= forceKillTime) {
				forceNextGeneration = true;
			}
		}

		this.species = new ArrayList<>(speciesManager.getSpecies().values());

		if(getLivingPopulation() < minimumPopulation) {
			respawnAgent(false);
		}

		calculateAgents();

		return true;

	}

	private boolean areResultsReady() {

		for(Future<String> future : this.updateResults) {
			if(!future.isDone())
				return false;
		}

		return true;
	}

	private void trimEntities() {
		for(int i = 0; i < entities.size(); i++) {
			Entity e = entities.get(i);

			if(e == null) {
				entities.remove(i);
				i--;
			}
			else if(!e.isAlive()) {
				entities.remove(e);
				i--;
			}
		}
	}

	private void checkAdditionsRemovals() {
		for(int i = 0; i < scheduleAdditions.size(); i++) {
			Entity e = scheduleAdditions.remove(0);

			if(e != null) {
				addEntity(e);
			}

			i--;
		}
		for(int i = 0; i < scheduledRemovals.size(); i++) {
			Entity e = scheduledRemovals.remove(0);

			if(e != null) {
				removeEntity(e);
			}

			i--;
		}
	}

	private void scheduledDisposes() {
		toDispose.forEach(d -> {
			if(d != null)
				d.dispose();
		});

		toDispose.clear();
	}

	private void createSpatialHash() {
		if(hashing == null)
			hashing = new SpatialHashing<>(Entity.class, (int) worldWidth, (int) Math.ceil(worldHeight / (Agent.getSizeOfSegment() * 2)));
		else
			hashing.clear();

		entities.forEach(e -> {
			if(e != null) {
				if(e.isAgent()) {
					Agent agent = (Agent) e;
					Vector2 loc = agent.getLocation();
					double size = Agent.getSizeOfSegment();
					if(agent.getHitbox() != null) {
						loc = agent.getHitbox().getLow().add(agent.getHitbox().getHigh()).divide(2).rotate(agent.getHitbox().getOrigin(), agent.getRelativeDirection());
						size = Math.max(agent.getStructure().getBounds().getWidth(), agent.getStructure().getBounds().getHeight()) * Agent.getSizeOfSegment();
					}
					hashing.store(agent, loc, size);
				}
				else
					hashing.store(e, e.getLocation(), e.getSize());
			}
		});
	}

	public static int getThreadCount() {
		return 32;
	}

	ExecutorService executor;

	private List<Future<String>> updateResults;

	private void calculateAgents() {
		if(executor == null)
			executor = Executors.newFixedThreadPool(getThreadCount());

		if(this.threads == null)
			startThreads(getThreadCount());

		this.threadEntities = new ConcurrentLinkedQueue<Entity>(this.entities);

		try {
			if(this.threads.size() == 1)
				this.threads.forEach(t -> t.call());
			else {
				List<Future<String>> results = new ArrayList<>(this.threads.size());
				this.threads.forEach(t -> results.add(executor.submit(t)));
				this.updateResults = results;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

	}

	private void startThreads(int threads) {
		this.threads = new ArrayList<>();
		Arrays.fill(this.threadsFinished, true);

		for(int i = 0; i < threads; i++) {
			CalculateTask task = new CalculateTask(this, i);
			this.threads.add(task);
		}
	}

	public boolean[] getThreadsFinished() {
		return threadsFinished;
	}

	private void createGraphs() {

		if(displayGraph != null)
			displayGraph.dispose();

		Pixmap totalGraph = engine.getDisplay().createLayeredGraph(engine.getDisplay().getImageWidth(),
				engine.getDisplay().getImageHeight(),
				new com.badlogic.gdx.graphics.Color[] { com.badlogic.gdx.graphics.Color.GREEN,
						com.badlogic.gdx.graphics.Color.YELLOW, com.badlogic.gdx.graphics.Color.RED,
						com.badlogic.gdx.graphics.Color.PURPLE },
				tracker.getTrackerData("photosynthesis").getData(),
				tracker.getTrackerData("chemosynthesis").getData(), tracker.getTrackerData("predation").getData(),
				tracker.getTrackerData("scavenged").getData());
		// Pixmap totalGraph = engine.getDisplay().createLayeredGraph(engine.getDisplay().getImageWidth(),
		// engine.getDisplay().getImageHeight(),
		// new com.badlogic.gdx.graphics.Color[] { com.badlogic.gdx.graphics.Color.GOLD },
		// tracker.getTrackerData("f").getData());

		displayGraph = new TextureTracker(totalGraph);
		totalGraph.dispose();
	}

	public Texture createGraphFrom(String string, com.badlogic.gdx.graphics.Color color) {
		Pixmap pixmap = engine.getDisplay().createGraph(tracker.getTrackerData(string).getData(),
				color,
				engine.getDisplay().getImageWidth(), engine.getDisplay().getImageHeight());
		Texture tex = new Texture(pixmap);
		pixmap.dispose();
		return tex;
	}

	public List<Agent> getToBuild() {
		return toBuild;
	}

	private void focusOnSelectedAgent() {
		if(selectedEntity != null) {
			engine.getDisplay().center = engine.getDisplay().transformWorldToScreen(selectedEntity.getLocation());
		}
	}

	private Border createBorder(int sides, double width, double height) {
		Polygon polygon = new Polygon();

		if(sides > 4) {
			for(int i = 0; i < sides; i++) {
				double theta = (double) ((((double) i / sides) * 2 - 1) * Math.PI);

				double x = Math.cos(theta) * worldWidth;
				double y = Math.sin(theta) * worldHeight;

				polygon.addPoint((int) x, (int) y);
			}
		}
		else {
			// make square have intuitive side lengths
			polygon.addPoint((int) -width / 2, (int) height / 2);
			polygon.addPoint((int) width / 2, (int) height / 2);
			polygon.addPoint((int) width / 2, (int) -height / 2);
			polygon.addPoint((int) -width / 2, (int) -height / 2);
		}

		Border border = new Border(this);
		border.addPolygon(polygon);

		List<Line2D> lines = new ArrayList<>();
		// add some random lines
		for(int i = 0; i < wallsInBorders; i++) {

			int x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)));
			int y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)));

			while(!border.contains(x, y)) { // || (Math.sqrt(x * x + y * y) < 250)
				x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)));
				y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)));
			}

			double length = 2;
			double angle = random.nextDouble() * Math.PI * 2;
			int x1 = (int) (x + (Math.cos(angle) * worldWidth / 2 * length));
			int y1 = (int) (y + (Math.sin(angle) * worldHeight / 2 * length));

			Vector2 w = null;
			if((w = border.getClosestLineIntersection(new Line2D.Double(x, y, x1, y1))) != null) {
				x1 = (int) w.x;
				y1 = (int) w.y;
			}

			// int div = (int) (size / 8);
			// x = (int) (Math.floor(x / div) * div);
			// y = (int) (Math.floor(y / div) * div);
			// x1 = (int) (Math.floor(x1 / div) * div);
			// y1 = (int) (Math.floor(y1 / div) * div);

			Line2D line = new Line2D.Double(x, y, x1, y1);
			lines.add(line);
		}
		lines.forEach(l -> border.addLine(l));

		// border.addLine(new Line2D.Double(-size, -0, -size / 10, -0));
		// border.addLine(new Line2D.Double(size / 10, 0, size, 0));

		border.build();

		// SimulationBody body = new SimulationBody();
		// org.dyn4j.geometry.Vector2 p1 = new org.dyn4j.geometry.Vector2(polygon.xpoints[0], polygon.ypoints[0]);
		// for(int i = 1; i < polygon.npoints; i++) {
		//
		// org.dyn4j.geometry.Vector2 p2 = new org.dyn4j.geometry.Vector2(polygon.xpoints[i], polygon.ypoints[i]);
		// body.addFixture(Geometry.createSegment(p1, p2));
		// p1 = p2;
		// }
		// body.addFixture(Geometry.createSegment(p1, new org.dyn4j.geometry.Vector2(polygon.xpoints[0], polygon.ypoints[0])));
		//
		// body.setLabel("hasCollision");
		// body.setMass(MassType.INFINITE);
		// dyn4.addBody(body);

		return border;
	}

	public void setCursorLocation(Vector2 location) {
		this.cursorEntity = location.copy();

		System.out.println(String.format("Shadows: %s, Light: %s, Chems: %s", getShadowsAt(location), getLightAt(location), getChemicalAt(location)));
		System.out.println("InCave: " + borders.isInCave(location));
	}

	public boolean isNearPredator(Vector2 loc, int range) {

		for(Agent t : this.speciesManager.getAllGeneticCarriers()) {
			if(t.getLocation().distanceToSq(loc) < range) {
				return true;
			}
		}

		return false;
	}

	public List<Agent> getSelectedSpecies() {
		if(selectedPopulation == -1)
			return this.speciesManager.getAllGeneticCarriers();
		return this.getPopulations().get(Math.max(Math.min(selectedPopulation, this.species.size() - 1), 0));
	}

	public void addFood(boolean isMeat) {

		int x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)) + 0);
		int y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)) + 0);

		while(!borders.getBounds().contains(x, y)) {
			x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)) + 0);
			y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)) + 0);
		}

		double noise = 1 - (getFoodNoise(x, y) + 1) / 2;
		if(random.nextDouble() > Math.pow(noise, 4)) // it can add food anywhere, but much more likely in certain regions
			return;

		if(isMeat)
			addMeatAt(new Vector2(x, y));
		else
			addBerryAt(new Vector2(x, y));
	}

	public double getFoodNoise(double x, double y) {
		return foodNoise.getSmoothNoiseAt(x, y, ticksSinceRestart / 10000.0 + noiseOffset);
	}

	public boolean isInFoodRegion(double x, double y) {
		if(foodNoise.getSmoothNoiseAt(x, y, ticksSinceRestart / 100000.0 + noiseOffset) > 0.0) {
			return true;
		}

		return false;
	}

	public boolean isInFoodRegion(Vector2 loc) {
		return isInFoodRegion(loc.x, loc.y);
	}

	public void addMeatAt(Vector2 location) {
		Meat e = new Meat(this, skillFactor * foodValue * 5 * (1 + (random.nextDouble() * 2 - 1) * 0.15), location);
		addEntity(e);
	}

	public void addBerryAt(Vector2 location) {
		Berry e = new Berry(this, skillFactor * foodValue * (1 + (random.nextDouble() * 2 - 1) * 0.15), location);
		addEntity(e);
	}

	public int attempt;

	/*
	 * This method is for the reproduction, death, and tax history of all entities
	 */

	public void setBestBrainImage(Agent agent, int width, int height) {
		if(bestBrainImage != null)
			bestBrainImage.dispose();

		if(agent == null || !agent.shouldExist()) {
			this.bestBrainImage = null;
			return;
		}

		Pixmap pixmap = LibDisplay.getImage(agent.getBrain(), width, height, true);
		bestBrainImage = new Texture(pixmap);
		pixmap.dispose();
	}

	public int getTotalPopulation() {
		return entities.size();
	}

	private void configureNewAgent(Agent agent, float position) {
		agent.setBrain(agent.createBrain());
		agent.mutate(1);
		agent.reset();
		agent.init();

		if(!wasLoadedFromSave) {
			double x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)));
			double y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)));

			while(!borders.contains(x, y) || borders.intersects(new Vector2(x, y))) {
				x = (int) (((random.nextDouble() * 2 - 1) * (worldWidth)));
				y = (int) (((random.nextDouble() * 2 - 1) * (worldHeight)));
			}

			agent.setLocation(new Vector2(x, y));
		}
	}

	public ArrayList<Entity> getEntities() {
		return entities;
	}

	public Engine getEngine() {
		return engine;
	}

	public int getCurrentGeneration() {
		return currentGeneration;
	}

	public Border getBorders() {
		return borders;
	}

	// public static FSTConfiguration fstConf = FSTConfiguration.createDefaultConfiguration();
	
	public boolean isSaving = false;

	public void save(boolean autosave) {

		if(isMultithreading()) {
			int i = 0;
			while(isMultithreading()) {
				if(i % 10000 == 0)
					System.out.println("Waiting for threads to finish before saving...");
				i++;
				continue;
			}
		}
		isSaving = true;

		// create new list to avoid thread nonsense
		File parent = new File(engine.getSaveLocationPath());

		DateFormat dateFormat = new SimpleDateFormat("yyyy_mm_dd__hh_mm");
		String strDate = dateFormat.format(Calendar.getInstance().getTime());
		String name = String.format("autosave_%s", ticksSinceRestart);

		if(!parent.exists())
			parent.mkdirs();

		int totalSaves = 0;
		for(File f : parent.listFiles())
			if(f.isDirectory() && f.getName().startsWith("autosave_"))
				totalSaves++;

		if(autosave)
			if(totalSaves >= 5) { // if there are too many saves, delete the oldest
				File oldest = null;
				long otime = 0;

				for(File f : parent.listFiles()) {
					if(f.isDirectory()) {
						long t = 0;
						try {
							t = Files.readAttributes(Paths.get(f.getAbsolutePath()),
									BasicFileAttributes.class).lastModifiedTime().toMillis();
						}
						catch(IOException e) {
							e.printStackTrace();
						}

						if(oldest == null || t < otime) {
							oldest = f;
							otime = t;
						}
					}
				}

				if(oldest != null) {
					deleteFile(oldest);
				}
			}

		File sub = new File(parent, name);
		sub.mkdirs();

		// save innovation history
		try {

			SaveState[] saveStates = new SaveState[this.entities.size()];
			int index = 0;
			for(Entity e : this.entities) {
				if(e != null) {
					saveStates[index] = e.createSaveState();
				}
				index++;
			}

			// boolean running = true;
			// while(running) {
			// getAngleOfSun();

			HashMap<String, Object> worldSave = new HashMap<String, Object>();
			worldSave.put("uwu", "owo");
			worldSave.put("ticksSinceRestart", this.ticksSinceRestart);
			worldSave.put("seed", this.seed);
			worldSave.put("innovationhistory", innovationHistory);
			worldSave.put("entities", saveStates);

			try {
				FileOutputStream fos = new FileOutputStream(new File(sub, "world.json"));
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(worldSave);

				oos.close();
				fos.close();
				fos = null;
				oos = null;

				// check that data is readable
				// try {
				// HashMap<String, Object> test = (HashMap<String, Object>) fstConf.asObject(data);
				// if(test != null && test.get("uwu").equals("owo")) {
				// System.out.println("Byte array could be read.");
				// System.out.println(test.toString());
				// }
				// else {
				// System.err.println("Byte array could NOT be read.");
				// System.err.println(test);
				// }
				// getAngleOfSun();
				// }
				// catch(Exception e) {
				// e.printStackTrace();
				// }

				FSTConfiguration.clearGlobalCaches();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			// }

		}
		catch(Exception e1) {
			e1.printStackTrace();
		}

		isSaving = false;
	}

	private void loadWorldSave(String path) {

		// load all brains into entities
		File parent = new File(path);
		File newest = null;
		long ntime = 0;

		// get latest save
		if(parent.exists())
			for(File f : parent.listFiles()) {
				if(f.isDirectory()) {

					if(f.getName().contains("autosave")) {
						long t = 0;
						try {
							t = Files.readAttributes(Paths.get(f.getAbsolutePath()),
									BasicFileAttributes.class).lastModifiedTime().toMillis();
						}
						catch(IOException e) {
							e.printStackTrace();
						}

						if(newest == null || t > ntime) {
							newest = f;
							ntime = t;
						}
					}
				}
			}

		if(newest != null) {
			parent = newest;
			List<Agent> loadedAgents = new ArrayList<>();
			// load innovation history
			try {
				FileInputStream fis = new FileInputStream(new File(parent, "world.json"));
				ObjectInputStream ois = new ObjectInputStream(fis);

				Object ob = ois.readObject();

				ois.close();
				fis.close();

				HashMap<String, Object> worldSave = (HashMap<String, Object>) ob;

				String handshake = (String) worldSave.get("uwu");
				if(!handshake.equals("owo")) {
					System.err.println("oopsie! wooks wike we gots a wittle bug still! world nu woad handshakes :(");
				}

				innovationHistory = (InnovationHistory) worldSave.get("innovationhistory");
				ticksSinceRestart = (int) worldSave.get("ticksSinceRestart");
				this.startTimeFromSave = this.ticksSinceRestart;
				seed = (int) worldSave.get("seed");

				SaveState[] savedEntities = (SaveState[]) worldSave.get("entities");
				// load each brain

				if(savedEntities == null || savedEntities.length == 0) {
					System.err.println("Couldn't load 'savedEntities'. Object is null or empty.");
					System.exit(1);
				}

				List<Entity> loadedEntities = new ArrayList<>(savedEntities.length);

				for(SaveState save : savedEntities) {
					Entity entity = Entity.load(this, save);

					if(entity.isAgent()) {
						((Agent) entity).getBrain().setInnovationHistory(innovationHistory);
						((Agent) entity).getBrain().update();
						loadedAgents.add((Agent) entity);
					}
					loadedEntities.add(entity);
				}

				if(!loadedAgents.isEmpty()) { // how empty? bug

					speciesManager = new SpeciesManager<Agent>(1, 1000000000, loadedAgents.get(0));
					speciesManager.distributeOrMakeNewSpecies(loadedAgents);
					loadedAgents.forEach(e -> {
						double energy = e.getEnergy();
						int age = e.getAge();
						double bodyMaintainanceEnergy = e.getBodyMaintainanceEnergy();
						e.reset();
						// change values back after resetting some regulatory values
						e.setEnergy(energy);
						e.setAge(age);
						e.setBodyMaintainanceEnergy(bodyMaintainanceEnergy);
					});

				}
				loadedEntities.forEach(e -> addEntity(e));
				loadedEntities.forEach(e -> e.init());

				wasLoadedFromSave = true;
			}
			catch(IOException e) {
				e.printStackTrace();
				if(e.getCause() != null)
					e.getCause().printStackTrace();
				System.exit(1);
			}
			catch(Exception e1) {
				e1.printStackTrace();
				System.exit(1);
			}

		}

	}

	private void deleteFile(File file) {

		// delete all subfiles if exists
		if(file.isDirectory()) {
			for(File f : file.listFiles()) {
				if(f.isDirectory()) {
					deleteFile(file);
				}
				else {
					f.delete();
				}
			}
		}

		file.delete();
	}

	public List<Species<Agent>> getPopulations() {
		return species;
	}

	public int getLastMedianAge() {
		return lastMedianAge;
	}

	public int getTime() {
		return ticksSinceRestart;
	}

	private int calculateLivingPopulation() {
		int count = 0;

		for(int i = 0; i < species.size(); i++) {
			Species<Agent> pop = species.get(i);
			count += pop.getLivingPopulation();
		}

		return count;
	}

	public int getLivingPopulation() {
		return lastLivingPopulation;
	}

	private Texture bestBrainImage;

	public Texture getBestBrainImage() {
		return bestBrainImage;
	}

	public int getSelectedPopulation() {
		return selectedPopulation;
	}

	public void setSelectedPopulation(int selectedPopulation) {
		this.selectedPopulation = selectedPopulation;
	}

	@Deprecated
	public double getTemperatureAt(Vector2 location) {
		Rectangle2D bounds = borders.getBounds();
		return (location.y - (bounds.getMinY())) / Math.abs(bounds.getMaxY() - (bounds.getMinY())) * 100;
	}

	public SpeciesManager<Agent> getSpeciesManager() {
		return speciesManager;
	}

	public List<Species<Agent>> getSpecies() {
		return species;
	}

	public List<java.lang.Double> getFitnessOverTime() {
		return fitnessOverTime;
	}

	public void deathAt(Agent agent) {
		// corpse with energy of corpse's body

		if(agent.willDropCorpse() && !Double.isNaN(agent.getTotalBodyEnergy())) {
			if(Double.isNaN(agent.getTotalBodyEnergy()))
				getCurrentGeneration();
			Corpse corpse = new Corpse(this, agent.getMass(), (agent.getTotalBodyEnergy()), agent.getLocation());
			corpse.setVelocity(agent.getVelocity());
			addEntity(corpse);
		}

		removeEntity(agent);
	}

	public void respawnAgent(boolean replace) {
		if(!realTime) {
			return;
		}

		Agent nextAgent = createDefaultAgent();
		nextAgent.mutate(1);
		addEntity(nextAgent);
		configureNewAgent(nextAgent, 0);

		speciesManager.distributeOrMakeNewSpecies(Arrays.asList(nextAgent));

	}

	public Entity[] getNearbyEntities(Rectangle2D bounds) {

		Entity[] list = hashing.query(bounds);

		return list;
	}

	public Entity[] getNearbyEntities(Vector2 center, float size) {

		Rectangle2D bounds = new Rectangle2D.Double(center.x - size, center.y - size, size * 2, size * 2);
		Entity[] list = hashing.query(bounds);

		return list;
	}

	public Vector2 getCursorEntity() {
		return cursorEntity;
	}

	public static InnovationHistory getInnovationHistory() {
		return innovationHistory;
	}

	public SpatialHashing<Entity> getSpatialHash() {
		return hashing;
	}

	public void setSelectedEntity(Entity selectedEntity) {
		this.selectedEntity = selectedEntity;

		if(selectedEntity != null && selectedEntity.isAgent())
			Gdx.app.postRunnable((Runnable) () -> {
				setBestBrainImage((Agent) selectedEntity, engine.getDisplay().getImageWidth(),
						engine.getDisplay().getImageHeight());
			});
	}

	public Entity getSelectedEntity() {
		return selectedEntity;
	}

	public NoveltyTracker<Agent> getNoveltyTracker() {
		return noveltyTracker;
	}

	public double getFoodValue() {
		return foodValue;
	}

	private Predicate<GeneticCarrier> selectionPredicateRemoveIf = new Predicate<GeneticCarrier>() {

		@Override
		public boolean test(GeneticCarrier t) {
			return !t.isAlive();
		}
	};

	public double getWorldWidth() {
		return worldWidth;
	}

	public double getWorldHeight() {
		return worldHeight;
	}

	// public void updateHash(Entity t) {
	// if(t == null)
	// return;
	//
	// if(hasStarted) // only check for this when it isnt loading
	// if(!entities.contains(t)) {
	// throw new IllegalArgumentException("Entity is not member of world list.");
	// }
	//
	// Coords c = hashing.getCoordsFromRawVector(t.getLocation());
	// if(t.getLastCoords() == null) { // entity been added
	// hashing.store(t, t.getSize());
	// t.setLastCoords(c);
	// return;
	// }
	//
	// if(t.getLastCoords() == c) { // entity hasnt moved from chunk
	//
	// }
	// else { // entity has moved
	// hashing.store(t, t.getSize());
	// t.setLastCoords(c);
	// }
	//
	// }

	public double getSkillFactor() {
		return skillFactor;
	}

	public int getHeatMapScale() {
		return heatMapScale;
	}

	public double getLightAt(Vector2 location) {
		if(borders.isInCave(location)) {
			return 0;
		}
		
//		Rectangle2D bounds = borders.getBounds2D();
//		double yValue = Math.abs(location.y - bounds.getMaxY()) / (bounds.getHeight());
//		yValue = Math.max(0, Math.min(1, 1 - yValue));
//
//		double lightLevels = Math.pow(yValue, 1);
//		lightLevels = Math.max(0, Math.min(1, lightLevels));
		

		return 1 * getDaytimeLightFactor();
	}

	public double getChemicalAt(Vector2 location) {
		
		Rectangle2D bounds = borders.getBounds2D();
		double yValue = Math.abs(location.y - bounds.getMaxY()) / (bounds.getHeight());
		yValue = Math.max(0, Math.min(1, yValue));

		double chemicalLevels = Math.pow(yValue, 8);
		chemicalLevels = Math.max(0, Math.min(1, chemicalLevels));
		
		if(borders.isInCave(location)) {
			chemicalLevels += 0.5;
		}

		return chemicalLevels;
	}

	private List<Agent> toBuild = new ArrayList<>();

	public void rerenderAgent(Agent agent) {
		if(toBuild.contains(agent))
			toBuild.remove(agent); // to stop repeated entries
		toBuild.add(agent);
	}

	public void addEntity(Entity entity) {
		if(isMultithreading()) {
			scheduleAdditions.add(entity);
			return;
		}
		if(entity == null)
			return;

		if(entity.isAgent() && !speciesManager.contains((Agent) entity)) {
			ArrayList<Agent> list = new ArrayList<>(1);
			list.add((Agent) entity);
			speciesManager.distributeOrMakeNewSpecies(list);
		}

		entities.add(entity);

		if(entity.isAgent()) {
			((Agent) entity).buildBody();
			toBuild.add((Agent) entity);
		}

		if(entity.getColor() == null) {
			System.out.println("null color");
		}
	}

	public boolean isMultithreading() {
		return Thread.currentThread().getId() != engine.getCurrentThreadID();
	}

	public void addAllEntities(Iterable<? extends Entity> entities) {
		entities.forEach(e -> addEntity(e));
	}

	private List<Disposable> toDispose;

	public void removeEntity(Entity entity) {
		if(isMultithreading()) {
			scheduledRemovals.add(entity);
			if(toBuild.contains(entity))
				toBuild.remove(entity);
		}
		else {
			this.entities.remove(entity);

			if(entity.isAgent()) {
				Agent a = ((Agent) entity);
				this.speciesManager.removeAgent(a);
				engine.getDisplay().removeAgent(a);
			}

			entity.dispose();
			if(toBuild.contains(entity))
				toBuild.remove(entity);
		}

	}

	public double getCurrentThetaAt(Vector2 location) {
		// rotate slightly so that the average (0.5) moves upwards
		return Math.PI / 2
				+ ((waterCurrents.getSmoothNoiseAt(location.x, location.y, ticksSinceRestart / 4000.0) + 1) / 2)
						* Math.PI * 2;
	}

	public Texture getCurrentGraph() {
		return displayGraph;
	}

	private double angleOfSun;

	public double getDaytimeLightFactor() {
		// sun intensity is equal to cos the elevation angle. elevation angle is the angle from the horizon
		return 1;//Math.max(0.0, Math.cos(angleOfSun - Math.PI / 2)); // minimum sun of 0
	}

	public double getAngleOfSun() {
		return angleOfSun;
	}

	private int dayLength = 15000;

	public void updateSun() {
		double thetaPerTick = (Math.PI * 2) / dayLength;

		angleOfSun = (Math.PI / 8) + this.ticksSinceRestart * thetaPerTick;
		while(angleOfSun > Math.PI * 2)
			angleOfSun -= Math.PI * 2;
		while(angleOfSun < 0)
			angleOfSun += Math.PI * 2;
	}

	public Entity getEntityByUUID(UUID fromString) {

		for(Entity e : this.entities)
			if(e != null && e.getUUID().equals(fromString))
				return e;

		return null;
	}

	public ConcurrentLinkedQueue<Entity> getThreadEntities() {
		return threadEntities;
	}

	public float getShadowsAt(Vector2 vector) {
		Rectangle2D bounds = this.getBorders().getBounds2D();
		byte[] map = engine.getDisplay().getShadowPixmapBytes();
		if(map == null)
			return 1;

		Vector2 shadowDims = engine.getDisplay().getShadowDimensions();

		int x = (int) (Math.max(0, Math.min(1, (vector.x - bounds.getMinX()) / bounds.getWidth())) * shadowDims.getX());
		int y = (int) (Math.max(0, Math.min(1, (vector.y - bounds.getMinY()) / bounds.getHeight())) * shadowDims.getY());

		int index = (int) Math.round(x + y * (shadowDims.getX()));
		if(index >= map.length)
			index = map.length - 1; // sometimes rounds above this value or a bug. idk, either way this will stop it
		int shadowByte = Byte.toUnsignedInt(map[index]);

		// System.out.println(shadowByte);
		float shadows = 1 - (shadowByte / 256f);
		if(shadows > 1)
			getAngleOfSun();

		// look, it needs this for some reason. The shader won't round this to zero
		shadows = (shadows - 0.00390625f) * (1.0f / (1 - 0.00390625f));

		return shadows;
	}

	public List<Agent> getAgents() {
		return this.speciesManager.getAllGeneticCarriers();
	}

	public boolean wasLoadedFromSave() {
		return wasLoadedFromSave;
	}

	public int getSeed() {
		return seed;
	}

}
