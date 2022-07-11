package me.brook.selection;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JFileChooser;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import me.brook.selection.LibDisplay.RenderingMode;
import me.brook.selection.tools.InputDetector;
import me.brook.selection.tools.Vector2;

public class Engine extends Game {

	public static final int DEFAULT_FPS = -5;

	private boolean running;

	private World world;
	private LibDisplay libDisplay;

	private int fps = DEFAULT_FPS;
	private RenderingMode renderingMode = RenderingMode.LOADING;
	private double zoomValue = 2.2;
	private boolean toggleFocus = false;
	private boolean speedUntilNextGeneration = false;
	private Random random;

	Vector2 cursor;

	protected boolean passiveMode = false;
	protected String[] args;
	private String saveLocationPath;

	public boolean isLoading = false;
	public boolean finishedLoading = false;
	private long currentThreadID;

	public Engine(String[] args) throws Exception {
		this.args = args;
		random = new Random();
		libDisplay = new LibDisplay(this);
		fps_history = new ArrayList<>(100);

		// cursor = new Vector2(display.getFrame().getWidth() / 2, display.getFrame().getHeight() / 2);
	}

	public void start() {
		running = true;
	}

	public void requestStop() {
		running = false;
	}

	private List<Digit> fps_history;

	private long fps_sum;
	private long fpsFreq = (long) (0.1 * 1e9);

	public int getAverageFps() {
		return (int) (1e9f / (fps_sum / Math.max(1, fps_history.size())));
	}

	public class Digit {
		public int value;

		public Digit(int value) {
			this.value = value;
		}
	}

	@Override
	public void render() {
		if(!finishedLoading) {
			isLoading = true;
			loadSome();
		}
		super.render();

		if(finishedLoading)
			tick();
	}

	long delay;
	long lastUpdate = System.nanoTime();

	public void tick() {
		currentThreadID = Thread.currentThread().getId();
		delay = (long) (1.0e9 / fps);

		try {
			long now = System.nanoTime();
			if((fps != 0 && lastUpdate + delay < now) || (fps < 0)) {
				world.update();
				int frames = (int) (now - lastUpdate);
				if(fps_history.size() > 100) {
					fps_sum -= fps_history.remove(0).value;
				}

				fps_sum += frames;
				fps_history.add(new Digit(frames));

				lastUpdate = now;
			}

		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	public LibDisplay getDisplay() {
		return libDisplay;
	}

	@Override
	public void create() {
		libDisplay.create();
	}

	public String loadingStage;

	private List<Runnable> queue;

	public boolean loadSome() {
		if(queue == null) {
			queue = new ArrayList<Runnable>();

			queue.add((Runnable) () -> {
				loadingStage = "Loading args";
				String path = new JFileChooser().getFileSystemView().getDefaultDirectory().toString() + "\\Snowfall";
				int arg = containsArgCommand("-o");
				if(arg != -1) {
					if(args.length > arg + 1)
						path = args[arg + 1];
				}
				this.saveLocationPath = path;
				loadingStage = "Loading world";
			});

			queue.add((Runnable) () -> {
				world = loadWorld(this.saveLocationPath);
				loadingStage = "Loading assets";
			});
			queue.add((Runnable) () -> {
				libDisplay.load();
				loadingStage = "Loading terrain";
			});
			queue.add((Runnable) () -> {
				world.getBorders().addWorldMap(world.getSeed(),
						(int) world.getBorders().getBounds().getWidth(), (int) world.getBorders().getBounds().getHeight());
				if(!world.wasLoadedFromSave())
					world.configureAllAgents();
				
				Gdx.input.setInputProcessor(new InputDetector(this));
				setRenderingMode(RenderingMode.ENTITIES);
			});

		}
		else {
			if(!queue.isEmpty()) {
				queue.remove(0).run();
			}
			else {
				finishedLoading = true;
				return true;
			}
		}

		return false;
	}

	private int containsArgCommand(String string) {

		for(int i = 0; i < args.length; i++) {
			String str = args[i];

			if(str.trim().equals(string))
				return i;
		}

		return -1;
	}

	static Engine _engine;

	public static void main(String[] args) throws Exception {
		boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
				.indexOf("-agentlib:jdwp") > 0;

		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle(isDebug ? "Snowfall Debug" : "Snowfall");
		config.setForegroundFPS(0);
		config.setIdleFPS(Integer.MAX_VALUE);

		double ratio = 16 / 9.0;
		config.setWindowedMode(1800, (int) Math.floor(1800 / ratio));
		config.setWindowIcon("assets/berry.png");
		config.setResizable(false);
		config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 8);

		hasDisplay = true;
		_engine = new Engine(args);

		config.setWindowListener(new Lwjgl3WindowAdapter() {
			@Override
			public boolean closeRequested() {
				_engine.shutdown();
				return super.closeRequested();
			}
		});
		
		if(hasDisplay)
			new Lwjgl3Application(_engine, config);
		else
			while(true) {
				_engine.tick();
			}
	}

	public int getFps() {
		return fps;
	}

	public void setFps(int fps) {
		this.fps = fps;
		System.out.println(fps);
	}

	public World getWorld() {
		return world;
	}

	public boolean shouldRenderEntities() {
		return renderingMode == RenderingMode.ENTITIES && !speedUntilNextGeneration;
	}

	public float getZoom() {
		return getZoom(this.zoomValue);
	}

	public float getZoom(double zoomValue) {
		return (float) Math.pow(2, zoomValue);
	}

	public void setZoomValue(double zoom) {
		this.zoomValue = zoom;
	}

	public double getZoomValue() {
		return zoomValue;
	}

	public Vector2 getCursor() {
		return cursor;
	}

	private Point mouse;

	private boolean[] keyStates = new boolean[10000];

	public boolean getKeyStatus(char c) {
		return keyStates[KeyEvent.getExtendedKeyCodeForChar(c)];
	}

	public boolean getKeyStatus(int c) {
		return keyStates[c];
	}

	public boolean isPassiveMode() {
		return passiveMode;
	}

	public void setRenderingMode(RenderingMode renderingMode) {
		this.renderingMode = renderingMode;
		libDisplay.updateScreen(renderingMode);
	}

	private World loadWorld(String path) {
		World world = null;
		try {
			boolean reload = true;
			if(reload) {
				world = new World(this, path, false);
			}
			else {
				world = new World(this, null, true);
			}
		}
		catch(FileNotFoundException e) {
			e.printStackTrace();
		}

		return world;
	}

	@Override
	public void dispose() {
		libDisplay.dispose();
	}

	public void shutdown() {
		libDisplay.dispose();
		world.save(false);
		running = false;
		Gdx.app.exit();
		System.exit(1);
	}

	public RenderingMode getRenderingMode() {
		return renderingMode;
	}

	private static boolean hasDisplay;

	public boolean hasDisplay() {
		return hasDisplay;
	}

	public String getSaveLocationPath() {
		return saveLocationPath;
	}
	
	public long getCurrentThreadID() {
		return currentThreadID;
	}

}
