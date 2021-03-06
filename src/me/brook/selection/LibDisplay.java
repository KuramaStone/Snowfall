package me.brook.selection;

import java.awt.Insets;
import java.awt.geom.Rectangle2D;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
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
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.neat.network.GConnection;
import me.brook.neat.network.NeatNetwork;
import me.brook.neat.network.NeatNeuron;
import me.brook.neat.network.Phenotype.Pheno;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Entity;
import me.brook.selection.entity.body.Hitbox;
import me.brook.selection.entity.body.Segment;
import me.brook.selection.entity.body.Structure;
import me.brook.selection.screens.EntityScreen;
import me.brook.selection.screens.FastScreen;
import me.brook.selection.screens.InfoScreen;
import me.brook.selection.screens.LoadingScreen;
import me.brook.selection.tools.SpatialHashing;
import me.brook.selection.tools.SpatialHashing.Chunk;
import me.brook.selection.tools.Vector2;

public class LibDisplay {

	private DecimalFormat dfdouble = new DecimalFormat("0.000");
	private Random random;

	public Engine engine;
	public AssetManager assets;

	public ShapeRenderer shapes;
	private ImmediateModeRenderer20 imModeRender;

	public OrthographicCamera worldCamera, staticCamera;

	public ShaderProgram worldShader, agentShader, structureShader, shadowShader, shadowMapShader;

	public SpriteBatch batch;
	PolygonSpriteBatch polybatch;

	public BitmapFont font;
	public Vector2 center;

	public Sprite berrySprite, agentSprite;

	private EntityScreen entityScreen;
	private LoadingScreen loadingScreen;
	private InfoScreen infoScreen;
	private FastScreen fastScreen;

	public TextureRegion lastShadowTexture;
	public Pixmap shadowPixmap;
	public FrameBuffer shadowBuffer;

	private Map<AgentTextureStage, HashSet<Agent>> usedTextures;
	private Map<AgentTextureStage, Sprite> texturesByDna;

	public RenderingMode prevMode;
	private byte[] shadowByteArray;
	private Vector2 shadowDimensions;
	private TextureRegion shadowBackgroundTexture;

	public LibDisplay(Engine engine) {
		this.engine = engine;
		random = new Random();

		center = new Vector2();
		usedTextures = new HashMap<>();
		texturesByDna = new HashMap<>();

		assets = new AssetManager();
		assets.load("assets/berry.png", Texture.class);
		assets.load("assets/meat.png", Texture.class);
		assets.load("assets/agent.png", Texture.class);
	}

	public void create() {
		batch = new SpriteBatch();
		polybatch = new PolygonSpriteBatch();
		shapes = new ShapeRenderer();
		imModeRender = new ImmediateModeRenderer20(1_000_000, false, true, 0);

		font = new BitmapFont();

		center = new Vector2(0, 0);

		entityScreen = new EntityScreen(this);
		infoScreen = new InfoScreen(this);
		loadingScreen = new LoadingScreen(this);
		fastScreen = new FastScreen(this);

		Pixmap pix = new Pixmap(1, 1, Pixmap.Format.Alpha);
		pix.setColor(new Color(0, 0, 1, 1f));
		pix.fill();
		Texture texture = new TextureTracker(pix);
		shadowBackgroundTexture = new TextureRegion(texture);
		pix.dispose();

		updateScreen(engine.getRenderingMode());
	}

	public void render() {

	}

	public void updateScreen(RenderingMode renderingMode) {
		if(renderingMode == RenderingMode.ENTITIES) {
			engine.setScreen(entityScreen);
		}
		else if(renderingMode == RenderingMode.NETWORK || renderingMode == RenderingMode.GRAPH) {
			engine.setScreen(infoScreen);
		}
		else if(renderingMode == RenderingMode.FAST) {
			engine.setScreen(fastScreen);
		}
		else if(renderingMode == RenderingMode.LOADING) {
			engine.setScreen(loadingScreen);
		}
		prevMode = renderingMode;

	}

	public void load() {

		engine.loadingStage = "Loading shaders";
		ShaderProgram.pedantic = false;
		agentShader = new ShaderProgram(Gdx.files.internal("assets/shaders/agentVertex.glsl"),
				Gdx.files.internal("assets/shaders/agentFragment.glsl"));
		worldShader = new ShaderProgram(Gdx.files.internal("assets/shaders/worldVertex.glsl"),
				Gdx.files.internal("assets/shaders/worldFragment.glsl"));
		structureShader = new ShaderProgram(Gdx.files.internal("assets/shaders/structureVertex.glsl"),
				Gdx.files.internal("assets/shaders/structureFragment.glsl"));

		shadowShader = new ShaderProgram(Gdx.files.internal("assets/shaders/shadowVertex.glsl"),
				Gdx.files.internal("assets/shaders/shadowFragment.glsl"));
		shadowMapShader = new ShaderProgram(Gdx.files.internal("assets/shaders/shadowMapVertex.glsl"),
				Gdx.files.internal("assets/shaders/shadowMapFragment.glsl"));

		if(!agentShader.isCompiled()) {
			System.err.println("Agent Shader");
			System.err.println(agentShader.getLog());
			System.exit(1);
		}
		if(!worldShader.isCompiled()) {
			System.err.println("World Shader");
			System.err.println(worldShader.getLog());
			System.exit(1);
		}
		if(!structureShader.isCompiled()) {
			System.err.println("Structure Shader");
			System.err.println(structureShader.getLog());
			System.exit(1);
		}
		if(!shadowShader.isCompiled()) {
			System.err.println("Shadow Shader");
			System.err.println(shadowShader.getLog());
			System.exit(1);
		}
		if(!shadowMapShader.isCompiled()) {
			System.err.println("shadowMap Shader");
			System.err.println(shadowMapShader.getLog());
			System.exit(1);
		}

		engine.loadingStage = "Loading assets";
		assets.finishLoading();
		agentSprite = new Sprite((Texture) assets.get("assets/agent.png"));
		berrySprite = new Sprite((Texture) assets.get("assets/berry.png"));

	}

	public void createCameras() {
		staticCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		staticCamera.translate((float) (getImageWidth() / 2), (float) -getImageHeight() / 2);
		staticCamera.update();

		worldCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		worldCamera.zoom = engine.getZoom();
		worldCamera.translate((float) center.x, (float) center.y);
		worldCamera.update();

	}

	public void buildAllAgents() {

		try {
			List<Agent> toBuild = engine.getWorld().getToBuild();

			for(int i = 0; i < toBuild.size(); i++) {
				Agent a = toBuild.remove(i);

				if(a == null || !a.isAlive()) {
					continue;
				}

				if(a.textureStatus != null) { // already built, remove
					// removeAgent(a);
				}

				buildBodyFor(a);
				i--;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

	}

	public void buildAgents() {

		try {
			List<Agent> toBuild = engine.getWorld().getToBuild();

			int amount = 250;

			for(int i = 0; i < toBuild.size(); i++) {
				Agent a = toBuild.remove(i);

				if(a == null || !a.isAlive()) {
					continue;
				}

				if(a.textureStatus != null) { // already built, remove
					// removeAgent(a);
				}

				buildBodyFor(a);
				i--;

				amount--;

				if(amount == 0)
					break;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

	}

	public Vector2 transformWorldToScreen(Vector2 world) {
		return world.multiply(new Vector2(1, 1));
	}

	public Vector2 transformScreenToWorld(Vector2 screen) {
		OrthographicCamera cam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

		cam.zoom = (float) getCurrentZoom();
		cam.translate((float) center.x, (float) center.y);
		cam.update();

		Vector3 unproject = cam.unproject(new Vector3((float) screen.x, (float) screen.y, 0));

		return new Vector2(unproject.x, unproject.y);
	}

	public int calcShadowWidth() {
		return (int) (engine.getWorld().getWorldWidth() / (Agent.getSizeOfSegment() * 2));
	}

	public int calcShadowHeight() {
		return (int) (engine.getWorld().getWorldHeight() / (Agent.getSizeOfSegment() * 2));
	}

	public void renderShadows() {
		int width = calcShadowWidth();
		int height = calcShadowHeight();
		Rectangle2D bounds = engine.getWorld().getBorders().getBounds2D();

		if(shadowBuffer == null)
			shadowBuffer = new FrameBuffer(Pixmap.Format.Alpha, width, height,
					false);

		Matrix4 matrix = new Matrix4();
		matrix.setToOrtho2D(0, 0, width, height);

		List<Entity> toRender = new ArrayList<>(engine.getWorld().getEntities());

		shadowBuffer.begin();
		ScreenUtils.clear(0, 0, 0, 0);
		drawShadows(polybatch, matrix, toRender, width, height, bounds);

		if(lastShadowTexture != null) {
			lastShadowTexture.getTexture().dispose();
		}

		if(shadowPixmap != null)
			shadowPixmap.dispose();
		shadowPixmap = new Pixmap(width, height, Pixmap.Format.Alpha);

		ByteBuffer buf;
		buf = shadowPixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_ALPHA, GL20.GL_UNSIGNED_BYTE, buf);

		Texture t = new TextureTracker(shadowPixmap);
		t.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		lastShadowTexture = new TextureRegion(t);
		lastShadowTexture.flip(false, true);

		shadowBuffer.end();
		// shadowBuffer.dispose();

		int length = buf.limit();
		byte[] array = new byte[length];
		buf.get(array);
		this.shadowByteArray = array;
		this.shadowDimensions = new Vector2(width, height);
	}

	private void drawShadows(PolygonSpriteBatch polybatch, Matrix4 matrix, List<Entity> toRender, int width, int height, Rectangle2D bounds) {
		polybatch.setProjectionMatrix(matrix);
		polybatch.enableBlending();

		polybatch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_CONSTANT_ALPHA);

		// polybatch.begin();
		PolygonRegion polyRegion;

		float alpha = 0.5f;
		polybatch.setColor(0, 0, 0, alpha);

		int maxVertices = (9) * toRender.size();

		org.lwjgl.opengl.GL20.glEnable(org.lwjgl.opengl.GL20.GL_BLEND);
		org.lwjgl.opengl.GL20.glBlendFunc(org.lwjgl.opengl.GL20.GL_ONE, org.lwjgl.opengl.GL20.GL_ONE_MINUS_CONSTANT_ALPHA);

		imModeRender.begin(matrix, GL20.GL_TRIANGLES);
		ScreenUtils.clear(0, 0, 0, 0);

		int shadowsRendered = 0;
		float colorBits = new Color(0, 0, 0, 0.5f).toFloatBits();
		for(int i = 0; i < toRender.size(); i++) {
			Entity entity = toRender.get(i);
			if(entity != null) {
				if(entity.isAgent()) {
					Agent agent = (Agent) entity;
					Hitbox box = agent.getHitbox();
					if(box == null)
						continue;
					
					if(engine.getWorld().getLightAt(agent.getLocation()) <= 0.01) {
						continue;
					}

					polyRegion = agent.getComputedShadowRegion();
					if(polyRegion == null)
						continue;
					
					// check if there are more than 1/alpha agents above it
//					double w = Math.max(agent.getStructure().getBounds().getWidth(), agent.getStructure().getBounds().getHeight());
//					Rectangle2D rect = new Rectangle2D.Double(agent.getLocation().x-w/2, agent.getLocation().y, w, Math.abs(bounds.getMaxY() - agent.getLocation().y));
//					Entity[] near = engine.getWorld().getNearbyEntities(rect);
//					
//					if(near.length > (1/ alpha + 1))
//						continue;

					float[] vertices = polyRegion.getVertices();
					
					for(int j = 0; j < vertices.length;) {
						imModeRender.color(colorBits);
						imModeRender.vertex(vertices[j++], vertices[j++], 0);
					}
					shadowsRendered++;

					// polybatch.draw(polyRegion, 0, 0);

					// shapes.rect(low.x, 0, high.x - low.x, low.y);
				}
			}
		}
//		System.out.println(shadowsRendered);

		imModeRender.end();
//		 polybatch.end();
		Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
	}

	private Vector2 toQuadVector(Vector2 vector2, int width, int height, Rectangle2D worldbounds) {
		float x = (float) (((vector2.x - worldbounds.getMinX()) / worldbounds.getWidth()) * width);
		float y = (float) (((vector2.y - worldbounds.getMinY()) / worldbounds.getHeight()) * height);
		x = Math.max(0, Math.min(x, width));
		y = Math.max(0, Math.min(y, height));

		return new Vector2(x, y);
	}

	public static Comparator<Vector2> viewShadowSorter = new Comparator<Vector2>() {

		@Override
		public int compare(Vector2 o1, Vector2 o2) {
			return (int) Math.signum(o1.x - o2.x);
		}
	};

	private double interpolate(double a, double b, double r) {
		return a + r * (b - a);
	}

	public void drawSpatialHash() {
		SpatialHashing<Entity> hash = engine.getWorld().getSpatialHash();
		if(hash == null)
			return;

		shapes.setProjectionMatrix(worldCamera.combined);
		shapes.begin(ShapeType.Line);
		shapes.setColor(Color.WHITE);

		for(Chunk<Entity> chunk : hash.getChunks().values()) {
			float x = chunk.getChunkX() * chunk.getSize();
			float y = chunk.getChunkY() * chunk.getSize();

			shapes.rect(x, y, (float) (chunk.getSize()),
					(float) (chunk.getSize()));
		}

		shapes.end();

	}

	public void drawInfo(boolean startBatch) {
		batch.setProjectionMatrix(staticCamera.combined);

		batch.setShader(null);
		if(startBatch)
			batch.begin();

		String finalPop = String.format("All: %s, Agents: %s", this.engine.getWorld().getEntities().size(),
				this.engine.getWorld().getSpeciesManager().getAllGeneticCarriers().size());
		// draw world info
		String time = getTimeFrom(engine.getWorld().getAngleOfSun());
		String timeInfo = String.format("Time: %s : %s", time, engine.getWorld().getTime());
		String fpsInfo = String.format("FPS: %s, %s", engine.getAverageFps(), Gdx.graphics.getFramesPerSecond());
		String ecoInfo = String.format("Sun: %s", dfdouble.format(engine.getWorld().getDaytimeLightFactor()));
		Runtime runtime = Runtime.getRuntime();
		String heapInfo = String.format("Heap: %skb / %s (%s)", (long) ((runtime.totalMemory() - runtime.freeMemory()) / 1e6), runtime.maxMemory() / 1e6,
				dfdouble.format(((double) runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory()));
		String skillInfo = String.format("Skill factor: %s", dfdouble.format(engine.getWorld().getSkillFactor()));

		float x = 3;
		float h = -font.getCapHeight() * 1.3f;
		float index = 0.3f;

		font.setColor(Color.RED);
		font.draw(batch, finalPop, x, index++ * h);
		font.draw(batch, timeInfo, x, index++ * h);
		font.draw(batch, ecoInfo, x, index++ * h);
		font.draw(batch, fpsInfo, x, index++ * h);
		font.draw(batch, "Textures: " + TextureTracker.activeTextures, x, index++ * h);
		font.draw(batch, heapInfo, x, index++ * h);
		font.draw(batch, skillInfo, x, index++ * h);

		index += 2;

		// draw entity info
		Entity sel = engine.getWorld().getSelectedEntity();
		if(sel != null) {

			if(sel.isAgent()) {
				Agent agent = (Agent) sel;
				// add selected agent info
				String ageInfo = String.format("Age: %s", agent.getAge());
				String energyInfo = String.format("Energy: %s : %s", agent.getEnergy(),
						agent.getEnergy() / agent.getReproductionEnergyThreshold());
				String ancestryInfo = String.format("Ancestry: %s : %s", agent.getGeneration(), agent.getChildren());
				String maturityInfo = String.format("Maturity: %s : %s", agent.getMaturity(), agent.getCurrentMetabolism());
				String foodInfo = String.format("Food: %s :: %s : %s",
						(agent.getTotalEnergyGained() + agent.getEnergyUsed() > 0 ? "+" : "-"), agent.getTotalEnergyGained(),
						(agent.getEnergyUsed()));
				double sum = Math.max(1, agent.getTotalEnergyGained());
				String dietInfo = String.format("Diet: %sphoto : %schems : %scarn", dfdouble.format(agent.getSunGained() / sum),
						dfdouble.format(agent.getChemGained() / sum), dfdouble.format(agent.getCarnGained() / sum));
				String miscEnergy = String.format("Body: %s, Stomach: %s", agent.getTotalBodyEnergy(), agent.getEnergyInStomach());
				String geneInfo = String.format("Body: %s", agent.getGeneDNA());
				String deathInfo = String.format("Death: %s", agent.getReasonForDeath());
				String nearbyInfo = "";
				if(agent.getLastNearbyEntities() != null)
					nearbyInfo = String.format("Nearby: %s", agent.getLastNearbyEntities().size());
				double c1 = agent.getEntitiesBlockingLightFactor(); // nearby agents reduce light for this agent
				double c2 = agent.getEntitiesBlockingChemsFactor(); // nearby agents reduce light for this agent
				String attachedInfo = String.format("Attached: %s", (sel.getAttachedTo() == null ? "null" : sel.getAttachedTo().getUUID().toString()));
				String velInfo = String.format("Vel: %s, %s", dfdouble.format(sel.getVelocity().x), dfdouble.format(sel.getVelocity().y));
				String incomeInfo = String.format("Sum: %s || In: %s, Out: %s", dfdouble.format(agent.getEnergyIncome() - agent.getEnergySpending()), dfdouble.format(agent.getEnergyIncome()), dfdouble.format(agent.getEnergySpending()));

				font.draw(batch, ageInfo, x, index++ * h);
				font.draw(batch, energyInfo, x, index++ * h);
//				font.draw(batch, ancestryInfo, x, index++ * h);
				font.draw(batch, maturityInfo, x, index++ * h);
				// font.draw(batch, foodInfo, x, index++ * h);
				font.draw(batch, dietInfo, x, index++ * h);
				// font.draw(batch, nearbyInfo, x, index++ * h);
//				font.draw(batch, "" + agent.getEntitiesBlockingLightFactor(), x, index++ * h);
//				font.draw(batch, miscEnergy, x, index++ * h);
				font.draw(batch, geneInfo, x, index++ * h);
				font.draw(batch, deathInfo, x, index++ * h);
//				font.draw(batch, "" + c1, x, index++ * h);
//				font.draw(batch, "" + c2, x, index++ * h);
//				font.draw(batch, agent.getUUID().toString(), x, index++ * h);
				font.draw(batch, attachedInfo, x, index++ * h);
				font.draw(batch, velInfo, x, index++ * h);
				font.draw(batch, incomeInfo, x, index++ * h);

				// visualize outputs

				batch.end();

				float size = 20;

				x += size * 1.1;
				float y = index * h - size;

				float startX = x;

				if(agent.getLastOutputs() != null) {
					Color[] colors = new Color[] {
							Color.GRAY,
							Color.WHITE, Color.WHITE,
							Color.PINK,
							Color.GREEN,
							Color.ORANGE,
							Color.GREEN,
							Color.RED,
							Color.WHITE,
							Color.SALMON,
							Color.SCARLET
					};
					shapes.setProjectionMatrix(staticCamera.combined);
					shapes.begin(ShapeType.Filled);
					int l = 0;
					if(agent.getLastOutputs() != null)
						for(double out : agent.getLastOutputs()) {
							shapes.setColor(colors[l]);
							shapes.circle(x, y, size);

							float theta = (float) (Math.PI / 2);
							float cos = (float) (Math.cos(theta) * size);
							float sin = (float) (Math.sin(theta) * size);
							shapes.setColor(Color.GRAY);
							shapes.rectLine(x, y, x + cos, y + sin, 2);
							x += size * 2 * 1.1;

							l++;
						}
					shapes.end();
					// now draw strings
					x = startX;
					batch.begin();
					font.getData().setScale(0.9f);
					for(double out : agent.getLastOutputs()) {

						font.setColor(Color.BLACK);
						GlyphLayout layout = new GlyphLayout();
						layout.setText(font, dfdouble.format(out));

						font.draw(batch, layout, x - (layout.width / 2), y + (layout.height / 2));
						x += size * 2 * 1.1;
					}
					font.getData().setScale(1);
					batch.end();
				}

				// draw health
				float screenSize = getImageWidth() * 0.15f;

				x = getImageWidth() - (screenSize * 1.1f);
				y = -10;
				shapes.setProjectionMatrix(staticCamera.combined);
				shapes.begin(ShapeType.Filled);
				shapes.setColor(Color.RED);
				shapes.rect(x, y, (float) (screenSize), h * 2);
				shapes.setColor(Color.GREEN);
				shapes.rect(x, y, (float) ((agent.getHealth() / agent.getMaxHealth()) * screenSize), h * 2);
				shapes.setColor(Color.NAVY);
				shapes.rect(x, y+h*2.2f, (float) (screenSize), h * 2);
				shapes.setColor(Color.BLUE);
				shapes.rect(x, y+h*2.2f, (float) ((agent.getStructure().getSumDevelopmentOfGrownCells() / agent.getStructure().getStructure().size()) * screenSize), h * 2);
				shapes.end();

				batch.begin();
				font.setColor(Color.BLACK);
				GlyphLayout layout = new GlyphLayout();
				layout.setText(font, dfdouble.format(agent.getHealth()) + " / " + dfdouble.format(agent.getMaxHealth()));
				font.draw(batch, layout, x, y);
				font.setColor(Color.WHITE);
				layout = new GlyphLayout();
				layout.setText(font, dfdouble.format(agent.getStructure().getSumDevelopmentOfGrownCells()) + " / " + agent.getStructure().getStructure().size());
				font.draw(batch, layout, x, y+h*2.2f);
				batch.end();

				Hitbox box = agent.getHitbox();
				if(engine.getRenderingMode() == RenderingMode.ENTITIES && box != null) {
					Vector2[] corners = box.getRotatedCorners(box.getOrigin(), box.getCurrentTheta());

					shapes.setProjectionMatrix(worldCamera.combined);
					shapes.begin(ShapeType.Line);

					for(int i = 0; i < corners.length; i++) {
						Vector2 v0 = corners[i];
						Vector2 v1 = corners[(i + 1) % (corners.length)];
						shapes.line(v0.x, v0.y, v1.x, v1.y);
					}

					shapes.end();
				}

				if(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
					shapes.setProjectionMatrix(worldCamera.combined);
					shapes.begin(ShapeType.Filled);
					Hitbox hitbox = agent.getHitbox();
					Structure structure = agent.getStructure();
					for(Segment seg : structure.getStructure()) {

						// get light received at location of each cell
						Vector2 loc = hitbox.getRotatedLow(new Vector2(), 0); // get bottom left of structure
						loc = loc.add(seg.getPosition().add(structure.getCoreOffset()).add(0.5, 0.5).multiply(Agent.getSizeOfSegment())); // get structure spot
						loc = loc.rotate(hitbox.getOrigin(), hitbox.getCurrentTheta()); // rotate it into position

						shapes.setColor(Color.PURPLE);
						shapes.circle(loc.x, loc.y, 1f);

					}

					shapes.end();
				}

			}
			else {
				String ageInfo = String.format("Age: %s", sel.getAge());
				String energyInfo = String.format("Energy: %s", sel.getEnergy());
				String attachedInfo = String.format("Attached: %s", (sel.getAttachedTo() == null ? "null" : sel.getAttachedTo().getUUID().toString()));
				String velInfo = String.format("Vel: %s, %s", dfdouble.format(sel.getVelocity().x), dfdouble.format(sel.getVelocity().y));

				font.draw(batch, ageInfo, x, index++ * h);
				font.draw(batch, energyInfo, x, index++ * h);
				font.draw(batch, attachedInfo, x, index++ * h);
				font.draw(batch, velInfo, x, index++ * h);
				batch.end();
			}
		}
		else {
			batch.end();
		}
		
		getCenter();
	}

	private DecimalFormat timeFormat = new DecimalFormat("00");

	private String getTimeFrom(double angle) {
		double thetaPerMinute = (Math.PI * 2) / (60 * 24);
		int minuteOfDay = (int) (angle / thetaPerMinute);

		int hour = (int) (minuteOfDay / 60);
		minuteOfDay -= hour * 60;

		return String.format("%s:%s", hour, timeFormat.format(minuteOfDay));
	}

	public Pixmap createLayeredGraph(int width, int height, Color[] colors, List<Double>... graphs) {
		SpriteBatch batch = new SpriteBatch();
		ShapeRenderer shapes = new ShapeRenderer();

		OrthographicCamera camera = new OrthographicCamera(width, height);
		camera.setToOrtho(true);
		camera.update();

		// we're going to render to a frame buffer and return that as a pixmap
		FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
				false);

		framebuffer.begin();

		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeType.Filled);
		shapes.setColor(Color.BLACK);
		shapes.rect(0, 0, width, height);

		float highest = 0;
		for(int j = 0; j < graphs.length; j++) {
			List<Double> list = graphs[j];
			for(int i = 0; i < list.size(); i++) {
				Double value = list.get(i);
				if(value > highest) {
					highest = value.floatValue();
				}
			}
		}
		highest *= 1.33f;

		// draw x/y lines
		Insets in = new Insets(10, 10, 10, 10);
		shapes.setColor(Color.WHITE);
		shapes.rectLine(in.left, in.bottom, width - in.right, in.bottom, 2f); // x line
		shapes.rectLine(in.left, in.bottom, in.left, height - in.top, 2f); // y line

		for(int j = 0; j < graphs.length; j++) {
			List<Double> list = graphs[j];

			float graphHeight = (height - in.top - in.bottom);

			Double start = list.get(0);
			float lx = in.left;
			float ly = (float) (in.bottom + (start / highest));
			// draw data
			shapes.setColor(colors[j]);
			for(int i = 0; i < list.size(); i++) {
				Double value = list.get(i);

				float nx = in.left + ((float) i / list.size()) * (width - in.left - in.right);
				float ny = (float) (in.bottom + graphHeight * (value / highest));

				shapes.rectLine(lx, ly, nx, ny, 2f);

				lx = nx;
				ly = ny;
			}

		}

		int xMarkers = Math.min(graphs[0].size(), 10);
		if(xMarkers == 0) {
			xMarkers = graphs[0].size();
		}
		double xMarkersPerNode = (graphs[0].size() / xMarkers);

		int markerWidth = (width - in.left - in.right) / xMarkers;
		shapes.setColor(Color.WHITE);
		for(int i = 0; i < xMarkers; i++) {
			float x = in.left + i * markerWidth;
			float y = in.bottom;

			int h = height / 100;
			shapes.line(x, y + h, x, y);
		}

		int yMarkers = 10;

		int markerHeight = (height - in.top - in.bottom) / yMarkers;
		for(int i = 0; i < yMarkers; i++) {
			float x = in.left;
			float y = in.bottom + i * markerHeight;

			int w = height / 100;
			shapes.line(x, y, x + w, y);
		}

		shapes.end();

		batch.begin();
		batch.setProjectionMatrix(camera.combined);
		font.setColor(Color.WHITE);
		for(int i = 0; i < xMarkers; i++) {
			float x = in.left + i * markerWidth;
			float y = in.bottom;

			int h = height / 100;
			font.draw(batch, "" + (int) (i * xMarkersPerNode), x - 2, y + h * 2);
		}
		for(int i = 0; i < yMarkers; i++) {
			float x = in.left;
			float y = in.bottom + i * markerHeight;

			int w = height / 100;
			font.draw(batch, dfdouble.format((int) (((double) i / xMarkers) * highest)), x + w, y);
		}

		batch.end();

		ByteBuffer buf;
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGB888);
		buf = pixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, buf);

		framebuffer.end();

		batch.dispose();
		shapes.dispose();
		framebuffer.dispose();

		return pixmap;
	}

	public Pixmap createGraph(List<Double> list, Color color, int width, int height) {
		ShapeRenderer shapes = new ShapeRenderer();
		SpriteBatch batch = new SpriteBatch();
		BitmapFont font = new BitmapFont();

		OrthographicCamera camera = new OrthographicCamera(width, height);
		camera.setToOrtho(true);
		camera.update();

		batch.setProjectionMatrix(camera.combined);

		// we're going to render to a frame buffer and return that as a pixmap
		FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
				false);

		framebuffer.begin();

		ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);

		shapes.begin(ShapeType.Filled);
		shapes.setColor(Color.WHITE);
		shapes.rect(0, 0, width, height);

		Insets in = new Insets(5, 5, 5, 5);
		// draw x/y lines
		shapes.setColor(Color.BLACK);
		shapes.line(in.left, in.bottom, width - in.right, in.bottom); // x line
		shapes.line(in.left, in.bottom, in.left, height - in.top); // y line

		float highest = 0;
		for(int i = 0; i < list.size(); i++) {
			Double value = list.get(i);
			if(value > highest) {
				highest = value.floatValue();
			}
		}
		highest *= 1.33f;

		float graphHeight = (height - in.top - in.bottom);

		Double start = list.get(0);
		float lx = in.left;
		float ly = (float) (in.bottom + (start / highest));
		// draw data
		shapes.setColor(color);
		for(int i = 0; i < list.size(); i++) {
			Double value = list.get(i);

			float nx = in.left + ((float) i / list.size()) * (width - in.left - in.right);
			float ny = (float) (in.bottom + graphHeight * (value / highest));

			shapes.line(lx, ly, nx, ny);

			lx = nx;
			ly = ny;
		}

		shapes.end();

		// we rendered everything! now write it to a pixmap and gtfo

		ByteBuffer buf;
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGB888);
		buf = pixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, buf);

		framebuffer.end();

		batch.begin();
		font.draw(batch, "HELLO", 0, 0);
		batch.end();

		framebuffer.dispose();
		batch.dispose();
		shapes.dispose();
		font.dispose();

		return pixmap;
	}

	public TextureRegion getShadowBackgroundTexture() {
		return shadowBackgroundTexture;
	}

	public static Pixmap getImage(NeatNetwork network, int width, int height, boolean drawWeights) {
		SpriteBatch spritebatch = new SpriteBatch();
		ShapeRenderer shapes = new ShapeRenderer();
		BitmapFont font = new BitmapFont();
		network.update();

		OrthographicCamera camera = new OrthographicCamera(width, height);
		camera.setToOrtho(true);
		camera.update();

		// we're going to render to a frame buffer and return that as a pixmap
		FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
				false);

		spritebatch.setProjectionMatrix(camera.combined);
		framebuffer.begin();

		ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);

		DecimalFormat df = new DecimalFormat("#.#####");

		shapes.begin(ShapeType.Filled);
		shapes.setColor(Color.WHITE);
		shapes.rect(0, 0, width, height);

		int r = 15;
		int layerGap = (width - r * 2) / (network.getLayersCount() - 1 + 1);

		for(int layerIndex = 0; layerIndex < network.getLayersCount(); layerIndex++) {

			// draw all the neurons in this layer
			int x = r + layerIndex * layerGap;

			List<NeatNeuron> layer = network.getLayers().get(layerIndex);
			// layer.removeIf(n -> n.getInputConnections().isEmpty() && n.getOutputConnections().isEmpty());

			double neuronGap = (((double) height) - r * 2.0) / (layer.size() + 2.0);
			for(int neuronIndex = 0; neuronIndex < layer.size(); neuronIndex++) {
				int y = height - (int) Math.round(r + neuronIndex * neuronGap + ((height / layer.size()) / 2));

				// draw connection to future neurons

				List<GConnection> outConnections = layer.get(neuronIndex).getOutputConnections();

				for(GConnection conn : outConnections) {

					NeatNeuron toNeuron = network.getNeuronByID(conn.getToNeuron());
					int x1 = r + (toNeuron.layerIndex) * layerGap;

					List<NeatNeuron> next = network.getLayers().get(toNeuron.layerIndex);
					// next.removeIf(n -> n.getInputConnections().isEmpty() && n.getOutputConnections().isEmpty());
					if(next.isEmpty()) {
						continue;
					}

					int ni = next.indexOf(network.getNeuronByID(conn.getToNeuron()));
					if(ni == -1) {
						throw new IllegalArgumentException("Neuron id is not in next layer list.");
					}

					int y1 = r + ni
							* ((height - r * 2) / (next.size() + 2));

					y1 += (height / next.size()) / 2;

					float w = (float) conn.getWeight();

					if(drawWeights) {
						int x2 = (x + x1) / 2;
						int y2 = height - ((height - y) + (y1)) / 2;

						shapes.end();
						spritebatch.begin();
						font.setColor(Color.BLACK);
						font.draw(spritebatch, df.format(conn.getWeight()), x2, y2);
						spritebatch.end();
						shapes.begin(ShapeType.Filled);
					}

					shapes.setColor(interpolateColor(Color.RED, Color.BLUE, Math.min(1, Math.max((w + 1) / 2, -1))));

					if(!conn.isEnabled()) {
						shapes.setColor(Color.BLACK);
					}

					shapes.rectLine(x, height - y, x1, y1, 2);
				}
				shapes.setColor(layer.get(neuronIndex).isBiasNeuron() ? Color.GREEN : Color.BLACK);
				// draw neuron
				shapes.circle(x, height - y, r);

				shapes.end();

				spritebatch.begin();
				font.setColor(Color.BLACK);
				font.draw(spritebatch, df.format(layer.get(neuronIndex).getOutput()), x + r * 2, y);

				if(layer.get(neuronIndex).getLabel() != null)
					font.draw(spritebatch, layer.get(neuronIndex).getLabel(), x + r * 2,
							y + font.getLineHeight());

				if(layer.get(neuronIndex).getTransferID() >= 0)
					font.draw(spritebatch, layer.get(neuronIndex).getTransferID() + "", x + r * 2,
							y + font.getLineHeight() * 2);

				spritebatch.end();
				shapes.begin(ShapeType.Filled);
			}

		}

		shapes.end();
		spritebatch.begin();
		font.setColor(Color.BLACK);

		// draw phenotype
		int y = r * 3;
		int index = 0;
		for(Entry<String, Pheno> set : network.getPhenotype().getPhenotypes().entrySet()) {
			int x = r + ((width - r * 2) / network.getPhenotype().getPhenotypes().size()) * index++;

			font.draw(spritebatch, String.valueOf(set.getKey()), x, y - font.getLineHeight());
			font.draw(spritebatch, df.format(set.getValue().getValue()), x, y);
		}

		spritebatch.end();

		// we rendered everything! now write it to a pixmap and gtfo

		ByteBuffer buf;
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGB888);
		buf = pixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, buf);

		framebuffer.end();

		spritebatch.dispose();
		framebuffer.dispose();
		shapes.dispose();
		font.dispose();

		return pixmap;
	}

	private static Color interpolateColor(Color COLOR1, Color COLOR2, float fraction) {
		final float INT_TO_FLOAT_CONST = 1f / 255f;
		fraction = Math.min(fraction, 1f);
		fraction = Math.max(fraction, 0f);

		final float RED1 = COLOR1.r;
		final float GREEN1 = COLOR1.g;
		final float BLUE1 = COLOR1.b;
		final float ALPHA1 = COLOR1.a;

		final float RED2 = COLOR2.r;
		final float GREEN2 = COLOR2.g;
		final float BLUE2 = COLOR2.b;
		final float ALPHA2 = COLOR2.a;

		final float DELTA_RED = RED2 - RED1;
		final float DELTA_GREEN = GREEN2 - GREEN1;
		final float DELTA_BLUE = BLUE2 - BLUE1;
		final float DELTA_ALPHA = ALPHA2 - ALPHA1;

		float red = RED1 + (DELTA_RED * fraction);
		float green = GREEN1 + (DELTA_GREEN * fraction);
		float blue = BLUE1 + (DELTA_BLUE * fraction);
		float alpha = ALPHA1 + (DELTA_ALPHA * fraction);

		red = Math.min(red, 1f);
		red = Math.max(red, 0f);
		green = Math.min(green, 1f);
		green = Math.max(green, 0f);
		blue = Math.min(blue, 1f);
		blue = Math.max(blue, 0f);
		alpha = Math.min(alpha, 1f);
		alpha = Math.max(alpha, 0f);

		return new Color(red, green, blue, alpha);
	}

	private double getCurrentZoom() {
		return engine.getZoom();
	}

	public void dispose() {
		batch.dispose();
		assets.dispose();
		shapes.dispose();
		font.dispose();
		agentShader.dispose();
		worldShader.dispose();
		structureShader.dispose();
		shadowShader.dispose();
		shadowMapShader.dispose();

		// dispose all textures
		this.texturesByDna.values().forEach(a -> {
			a.getTexture().dispose();
		});
	}

	public int getImageWidth() {
		return (int) Gdx.graphics.getWidth();
	}

	public int getImageHeight() {
		return (int) Gdx.graphics.getHeight();
	}

	public void refreshScreen() {

	}

	public Vector2 getCenter() {
		return center;
	}

	public void setCenter(Vector2 center) {
		this.center = center;
	}

	public AssetManager getAssets() {
		return assets;
	}

	public static enum RenderingMode {
		LOADING, ENTITIES, NETWORK, FAST, GRAPH;
	}

	public int getSegmentSpriteSize() {
		return Agent.getSizeOfSegment();
	}

	public void buildBodyFor(Agent agent) {
		if(!agent.isAlive())
			return;

		if(agent.getBodySprite() != null) {
			agent.getBodySprite().getTexture().dispose();
		}

		// AgentTextureStage ats = new AgentTextureStage(agent.getGeneDNA(), agent.getGrowthStage());
		// if(texturesByDna.containsKey(ats)) {
		// agent.setBodySprite(texturesByDna.get(ats));
		// HashSet<Agent> list = usedTextures.get(ats);
		// list.add(agent);
		// agent.textureStatus = "linked";
		// return;
		// }

		Structure structure1 = agent.getStructure();

		Rectangle2D maxBounds = structure1.getMaxBounds();
		float segWidth = (float) maxBounds.getWidth();
		float segHeight = (float) maxBounds.getHeight();

		int segmentSize = getSegmentSpriteSize() * 4;
		int width = (int) (segmentSize * segWidth);
		int height = (int) (segmentSize * segHeight);

		OrthographicCamera camera = new OrthographicCamera(width, height);
		camera.translate(width / 2, height / 2);
		camera.update();

		// we're going to render to a frame buffer and return that as a pixmap
		FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGB888, width, height,
				false);
		framebuffer.begin();
		ScreenUtils.clear(0, 0, 1, 0);

		batch.setProjectionMatrix(camera.combined);
		batch.setShader(structureShader);
		batch.begin();
		structureShader.bind();
		List<Segment> segments = structure1.getStructure();

		float w = (float) (maxBounds.getWidth());
		float h = (float) (maxBounds.getHeight());

		Vector2 offset = structure1.getMaxCoreOffset();

		int index = 0;
		for(int i = 0; i < Math.min(50, segments.size()); i++) { // maximum of 50 segments to render, 400 floats total
			Segment seg = segments.get(i);
			if(seg.getDevelopment() == 0)
				continue;

			int a = index * 6 + 0;
			int b = index * 6 + 1;
			int c = index * 6 + 2;
			int d = index * 6 + 3;
			int e = index * 6 + 4;
			int f = index * 6 + 5;
			Color color = getColorFromType(seg.getType());

			long seed = agent.getGeneDNA().hashCode();
			random.setSeed(seed);
			// Vector2 id = new Vector2(random.nextDouble() - 0.5, random.nextDouble() - 0.5);
			Vector2 id = new Vector2();

			structureShader.setUniformf("positions[" + a + "]", (0.5f + seg.getPosition().x + offset.x + id.x) / (w) - 0.5f);
			structureShader.setUniformf("positions[" + b + "]", (0.5f + seg.getPosition().y + offset.y + id.y) / (h) - 0.5f);
			structureShader.setUniformf("positions[" + c + "]", (float) (seg.getStrengthValue() * seg.getDevelopment()));
			structureShader.setUniformf("positions[" + d + "]", color.r);
			structureShader.setUniformf("positions[" + e + "]", color.g);
			structureShader.setUniformf("positions[" + f + "]", color.b);

			index++;
		}
		structureShader.setUniformi("totalPos", segments.size());
		structureShader.setUniformf("hue", agent.getHue());
		structureShader.setUniformf("width", width);
		structureShader.setUniformf("height", height);
		structureShader.setUniformf("id", (float) (Math.random() * 10000));
		structureShader.setUniformf("segmentWidth", (int) maxBounds.getWidth());
		structureShader.setUniformf("segmentHeight", (int) maxBounds.getHeight());

		// the shader does all the work
		batch.draw(agentSprite, 0, 0, width, height);
		batch.end();

		ByteBuffer buf;
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGB888);
		buf = pixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, buf);

		// we rendered everything! now write it to a pixmap and gtfo
		framebuffer.end();
		framebuffer.dispose();

		Texture texture = new TextureTracker(pixmap);
		Sprite sprite = new Sprite(texture);
		agent.setBodySprite(sprite);
		pixmap.dispose();

		// HashSet<Agent> list = new HashSet<>();
		// list.add(agent);

		agent.textureStatus = "generated";
		// texturesByDna.put(ats, sprite);
		// usedTextures.put(ats, list);
	}

	public static class AgentTextureStage {
		public String dna;
		public int growthStage; // stage of growth

		public AgentTextureStage(String dna, int growthStage) {
			this.dna = dna;
			this.growthStage = growthStage;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dna == null) ? 0 : dna.hashCode());
			result = prime * result + growthStage;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			AgentTextureStage other = (AgentTextureStage) obj;
			if(dna == null) {
				if(other.dna != null)
					return false;
			}
			else if(!dna.equals(other.dna))
				return false;
			if(growthStage != other.growthStage)
				return false;
			return true;
		}

	}

	private Color getColorFromType(String type) {
		if(type.equals(Structure.CORE)) {
			return Color.WHITE;
		}
		else if(type.equals(Structure.PHOTO)) {
			return Color.GREEN;
		}
		else if(type.equals(Structure.CHEMO)) {
			return Color.ORANGE;
		}
		else if(type.equals(Structure.HUNT)) {
			return Color.RED;
		}
		else if(type.equals(Structure.THRUST)) {
			return Color.DARK_GRAY;
		}

		return null;
	}

	public Map<AgentTextureStage, HashSet<Agent>> getUsedTextures() {
		return usedTextures;
	}

	public void removeTexture(AgentTextureStage ats) {
		if(this.texturesByDna.containsKey(ats)) {
			this.texturesByDna.remove(ats).getTexture().dispose();
		}
	}

	public void removeAgent(Agent a) {
		// AgentTextureStage toRemove = null;
		// for(AgentTextureStage ats : usedTextures.keySet()) {
		// HashSet<Agent> set = usedTextures.get(ats);
		//
		// if(set.contains(a)) {
		// toRemove = ats;
		// break;
		// }
		// }
		//
		// if(toRemove != null) {
		// HashSet<Agent> set = usedTextures.get(toRemove);
		// set.remove(a);
		// a.textureStatus = null;
		//
		// if(set.isEmpty()) {
		// usedTextures.remove(toRemove);
		// texturesByDna.remove(toRemove);
		// }
		//
		// }

	}

	public Map<AgentTextureStage, Sprite> getTexturesByDna() {
		return texturesByDna;
	}

	public void checkDisposal() {

		// System.out.println("==== HEAP ====");
		// for(String path : TextureTracker.totalTextures.keySet()) {
		// List<TextureTracker> inUse = TextureTracker.totalTextures.get(path);
		//
		// System.out.println(inUse.size() + " @ " + path);
		// }
		// System.out.println("==== HEAP ====");

		for(AgentTextureStage ats : this.usedTextures.keySet()) {
			if(this.usedTextures.get(ats).isEmpty()) {
				this.texturesByDna.get(ats).getTexture().dispose();
			}
		}
	}

	public static String getStackTracePath(int ignore) {
		StackTraceElement[] ele = Thread.currentThread().getStackTrace();

		StringBuilder sb = new StringBuilder();
		for(int i = ignore; i < ele.length; i++) {
			StackTraceElement stackTraceElement = ele[i];
			sb.append(stackTraceElement.toString());
			if(i != ele.length - 1)
				sb.append(" -> ");
		}

		return sb.toString();
	}

	public static class TextureTracker extends Texture {

		public static int activeTextures = 0;
		public static Map<String, List<TextureTracker>> totalTextures = new HashMap<>();
		private List<TextureTracker> reference;

		public TextureTracker(Pixmap pixmap) {
			super(pixmap);
			activeTextures++;

			String path = getStackTracePath(2);
			List<TextureTracker> list = totalTextures.get(path);
			if(list == null)
				totalTextures.put(path, list = new ArrayList<>());
			list.add(this);
			reference = list;
		}

		@Override
		public void dispose() {
			super.dispose();

			if(reference.contains(this)) {
				activeTextures--;
				reference.remove(this);
			}
		}

	}
	
	public boolean isLocationVisibleOnScreen(Vector2 location) {
		Vector3 projected = worldCamera.project(new Vector3(location.x, location.y, 0));
		return !(projected.x < -(2 * Agent.getSizeOfSegment()) || projected.y < -(2 * Agent.getSizeOfSegment()) ||
				projected.x > worldCamera.viewportWidth + (2 * Agent.getSizeOfSegment()) ||
				projected.y > worldCamera.viewportHeight + (2 * Agent.getSizeOfSegment()));
	}

	public byte[] getShadowPixmapBytes() {
		return shadowByteArray;
	}

	public Vector2 getShadowDimensions() {
		return shadowDimensions;
	}

	// public Pixmap getShadowPixmap() {
	// return shadowPixmap;
	// }

}
